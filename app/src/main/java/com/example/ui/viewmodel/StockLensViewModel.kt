package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.InventoryScannerService
import com.example.data.repository.InventoryRepository
import com.example.model.ActivityLog
import com.example.model.InventoryItem
import com.example.model.PlanTier
import com.example.model.ScanAnalysisResult
import com.example.model.ScanType
import com.example.model.ScannedItemCandidate
import com.example.model.StoreLocation
import com.example.model.UserPersona
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data class Analyzing(val scanType: ScanType, val previewBitmap: Bitmap) : ScanUiState
    data class Review(val result: ScanAnalysisResult, val previewBitmap: Bitmap) : ScanUiState
    data object Applying : ScanUiState
    data class Applied(val count: Int, val scanType: ScanType) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

enum class InventoryFilter(val label: String) {
    ALL("All"),
    LOW_STOCK("Low Stock"),
    EXPIRING("Expiring Soon"),
    OVERSTOCK("Overstock")
}

data class StoreHealthSummary(
    val store: StoreLocation,
    val itemCount: Int,
    val totalValue: Double,
    val lowStockCount: Int,
    val expiringCount: Int,
    val overstockCount: Int
)

data class WorkspaceSnapshot(
    val profile: UserProfile?,
    val persona: UserPersona,
    val tier: PlanTier,
    val stores: List<StoreLocation>,
    val activeStore: StoreLocation?,
    val storeItems: List<InventoryItem>,
    val allItems: List<InventoryItem>,
    val storeLogs: List<ActivityLog>,
    val allLogs: List<ActivityLog>,
    val lowStockItems: List<InventoryItem>,
    val expiringItems: List<InventoryItem>,
    val overstockItems: List<InventoryItem>,
    val totalStoreValue: Double,
    val totalWasteCost: Double,
    val storeHealthSummaries: List<StoreHealthSummary>
)

class StockLensViewModel(
    private val repository: InventoryRepository,
    private val scannerService: InventoryScannerService,
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String?
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(InventoryFilter.ALL)
    val selectedFilter: StateFlow<InventoryFilter> = _selectedFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _selectedScanType = MutableStateFlow(ScanType.RECEIPT)
    val selectedScanType: StateFlow<ScanType> = _selectedScanType.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val profileFlow = repository.observeUserProfile(currentUserId)
    private val storesFlow = repository.observeStores(currentUserId)
    private val itemsFlow = repository.observeItems(currentUserId)
    private val logsFlow = repository.observeActivityLogs(currentUserId)

    val workspaceState: StateFlow<DataUiState<WorkspaceSnapshot>> =
        combine(profileFlow, storesFlow, itemsFlow, logsFlow) { profile, stores, items, logs ->
            val persona = UserPersona.fromKey(profile?.persona)
            val tier = PlanTier.fromKey(profile?.tier)
            val activeStore = stores.firstOrNull { it.id == profile?.activeStoreId }
                ?: stores.firstOrNull()
            val activeStoreId = activeStore?.id.orEmpty()

            val storeItems = if (activeStoreId.isNotEmpty()) {
                items.filter { it.storeId == activeStoreId }
            } else {
                items
            }
            val storeLogs = if (activeStoreId.isNotEmpty()) {
                logs.filter { it.storeId == activeStoreId }
            } else {
                logs
            }

            val nowMs = System.currentTimeMillis()
            val lowStock = storeItems.filter { it.isLowStock }
            val expiring = storeItems.filter {
                val days = it.daysUntilExpiry(nowMs)
                days != null && days <= 3
            }.sortedBy { it.expiryEpochMs }
            val overstock = storeItems.filter { it.isOverstocked }
            val totalValue = storeItems.sumOf { it.totalValue }
            val wasteCost = storeLogs.filter { it.type == "waste" }.sumOf { it.estimatedValue }

            val summaries = stores.map { store ->
                val sItems = items.filter { it.storeId == store.id }
                StoreHealthSummary(
                    store = store,
                    itemCount = sItems.size,
                    totalValue = sItems.sumOf { it.totalValue },
                    lowStockCount = sItems.count { it.isLowStock },
                    expiringCount = sItems.count {
                        val d = it.daysUntilExpiry(nowMs)
                        d != null && d <= 3
                    },
                    overstockCount = sItems.count { it.isOverstocked }
                )
            }

            WorkspaceSnapshot(
                profile = profile,
                persona = persona,
                tier = tier,
                stores = stores,
                activeStore = activeStore,
                storeItems = storeItems,
                allItems = items,
                storeLogs = storeLogs,
                allLogs = logs,
                lowStockItems = lowStock,
                expiringItems = expiring,
                overstockItems = overstock,
                totalStoreValue = totalValue,
                totalWasteCost = wasteCost,
                storeHealthSummaries = summaries
            )
        }
            .map<WorkspaceSnapshot, DataUiState<WorkspaceSnapshot>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing workspace", rootError)
                emit(DataUiState.Error(rootError.message ?: "Failed to load inventory workspace"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DataUiState.Loading
            )

    init {
        viewModelScope.launch {
            repository.ensureInitialWorkspace(
                userId = currentUserId,
                displayName = initialDisplayName,
                email = initialEmail
            ).onFailure { err ->
                Log.e(TAG, "Initial workspace setup failed", err)
                _actionMessage.value = err.localizedMessage ?: "Failed to initialize workspace"
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFilter(filter: InventoryFilter) {
        _selectedFilter.value = filter
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSelectedScanType(scanType: ScanType) {
        _selectedScanType.value = scanType
    }

    fun resetScanState() {
        _scanState.value = ScanUiState.Idle
    }

    fun analyzeCapturedImage(bitmap: Bitmap, scanType: ScanType) {
        val current = (workspaceState.value as? DataUiState.Success)?.data
        val persona = current?.persona ?: UserPersona.PANTRY
        val existingItems = current?.storeItems ?: emptyList()

        viewModelScope.launch {
            _scanState.value = ScanUiState.Analyzing(scanType, bitmap)
            val result = scannerService.analyzePhoto(
                bitmap = bitmap,
                scanType = scanType,
                persona = persona,
                existingItems = existingItems
            )
            result.fold(
                onSuccess = { analysis ->
                    if (analysis.items.isEmpty()) {
                        _scanState.value = ScanUiState.Error(
                            "No recognizable stock or food items were found. Try a closer, well-lit photo."
                        )
                    } else {
                        _scanState.value = ScanUiState.Review(analysis, bitmap)
                    }
                },
                onFailure = { error ->
                    _scanState.value = ScanUiState.Error(
                        error.localizedMessage ?: "Failed to analyze photo with Gemini."
                    )
                }
            )
        }
    }

    fun toggleCandidateSelected(tempId: String) {
        val review = _scanState.value as? ScanUiState.Review ?: return
        val updated = review.result.items.map { item ->
            if (item.tempId == tempId) item.copy(selected = !item.selected) else item
        }
        _scanState.value = review.copy(result = review.result.copy(items = updated))
    }

    fun updateCandidateQuantity(tempId: String, newQuantity: Double) {
        val review = _scanState.value as? ScanUiState.Review ?: return
        val updated = review.result.items.map { item ->
            if (item.tempId == tempId) item.copy(quantity = newQuantity.coerceAtLeast(0.1)) else item
        }
        _scanState.value = review.copy(result = review.result.copy(items = updated))
    }

    fun updateCandidate(updatedCandidate: ScannedItemCandidate) {
        val review = _scanState.value as? ScanUiState.Review ?: return
        val updated = review.result.items.map { item ->
            if (item.tempId == updatedCandidate.tempId) updatedCandidate else item
        }
        _scanState.value = review.copy(result = review.result.copy(items = updated))
    }

    fun commitScannedItems() {
        val review = _scanState.value as? ScanUiState.Review ?: return
        val snapshot = (workspaceState.value as? DataUiState.Success)?.data ?: return
        val storeId = snapshot.activeStore?.id ?: return

        viewModelScope.launch {
            _scanState.value = ScanUiState.Applying
            val result = repository.applyScanResults(
                userId = currentUserId,
                storeId = storeId,
                scanType = review.result.scanType,
                candidates = review.result.items,
                existingItems = snapshot.storeItems
            )
            result.fold(
                onSuccess = { count ->
                    _scanState.value = ScanUiState.Applied(count, review.result.scanType)
                    _actionMessage.value = "Updated $count inventory items from ${review.result.scanType.title}."
                },
                onFailure = { err ->
                    _scanState.value = ScanUiState.Error(
                        err.localizedMessage ?: "Failed to save scanned items."
                    )
                }
            )
        }
    }

    fun switchPersona(persona: UserPersona) {
        viewModelScope.launch {
            repository.updateUserSettings(currentUserId, persona = persona)
                .onSuccess {
                    _actionMessage.value = "Switched to ${persona.title} profile."
                }
                .onFailure {
                    _actionMessage.value = it.localizedMessage ?: "Could not switch persona."
                }
        }
    }

    fun switchPlanTier(tier: PlanTier) {
        viewModelScope.launch {
            repository.updateUserSettings(currentUserId, tier = tier)
                .onSuccess {
                    _actionMessage.value = if (tier == PlanTier.PREMIUM) {
                        "Premium workspace unlocked! Multi-store & advanced analytics enabled."
                    } else {
                        "Switched to Free plan."
                    }
                }
                .onFailure {
                    _actionMessage.value = it.localizedMessage ?: "Could not switch plan."
                }
        }
    }

    fun switchActiveStore(storeId: String) {
        viewModelScope.launch {
            repository.updateUserSettings(currentUserId, activeStoreId = storeId)
                .onFailure {
                    _actionMessage.value = it.localizedMessage ?: "Could not switch store."
                }
        }
    }

    fun createStore(name: String, type: String, address: String?) {
        val snapshot = (workspaceState.value as? DataUiState.Success)?.data
        if (snapshot?.tier == PlanTier.FREE && snapshot.stores.isNotEmpty()) {
            _actionMessage.value = "Multi-store management requires Premium. Upgrade in Profile."
            return
        }
        viewModelScope.launch {
            repository.createStore(currentUserId, name, type, address)
                .onSuccess { newStoreId ->
                    repository.updateUserSettings(currentUserId, activeStoreId = newStoreId)
                    _actionMessage.value = "Created location \"$name\"."
                }
                .onFailure {
                    _actionMessage.value = it.localizedMessage ?: "Failed to create store."
                }
        }
    }

    fun deleteStore(storeId: String) {
        val snapshot = (workspaceState.value as? DataUiState.Success)?.data ?: return
        val fallbackStoreId = snapshot.stores.firstOrNull { it.id != storeId }?.id
        viewModelScope.launch {
            repository.deleteStore(currentUserId, storeId)
                .onSuccess {
                    if (snapshot.activeStore?.id == storeId && fallbackStoreId != null) {
                        repository.updateUserSettings(currentUserId, activeStoreId = fallbackStoreId)
                    }
                    _actionMessage.value = "Location removed."
                }
                .onFailure {
                    _actionMessage.value = it.localizedMessage ?: "Failed to remove location."
                }
        }
    }

    fun saveItem(item: InventoryItem) {
        val snapshot = (workspaceState.value as? DataUiState.Success)?.data ?: return
        val storeId = item.storeId.ifBlank { snapshot.activeStore?.id.orEmpty() }
        if (storeId.isBlank()) return

        viewModelScope.launch {
            repository.upsertItem(
                currentUserId,
                item.copy(userId = currentUserId, storeId = storeId)
            ).onSuccess {
                _actionMessage.value = "Saved ${item.name}."
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to save item."
            }
        }
    }

    fun adjustStock(item: InventoryItem, delta: Double, logType: String = "adjustment", reason: String? = null) {
        viewModelScope.launch {
            repository.adjustItemQuantity(currentUserId, item, delta, logType, reason)
                .onFailure {
                    _actionMessage.value = it.localizedMessage ?: "Failed to update quantity."
                }
        }
    }

    fun logWaste(item: InventoryItem, wastedQty: Double, reason: String) {
        val safeQty = wastedQty.coerceIn(0.1, item.quantity.coerceAtLeast(0.1))
        viewModelScope.launch {
            repository.adjustItemQuantity(
                userId = currentUserId,
                item = item,
                delta = -safeQty,
                logType = "waste",
                reason = reason
            ).onSuccess {
                _actionMessage.value = "Logged waste for ${item.name}."
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to log waste."
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteItem(currentUserId, itemId)
                .onSuccess { _actionMessage.value = "Item deleted." }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to delete item." }
        }
    }

    fun requestRestock(item: InventoryItem, orderQty: Double, reason: String) {
        viewModelScope.launch {
            repository.createRestockOrder(
                userId = currentUserId,
                storeId = item.storeId,
                item = item,
                orderQty = orderQty,
                reason = reason
            ).onSuccess {
                _actionMessage.value = "Restock order created for ${item.name}."
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to create restock order."
            }
        }
    }

    fun autoGenerateRestockOrders() {
        val snapshot = (workspaceState.value as? DataUiState.Success)?.data ?: return
        val pendingItemIds = snapshot.storeLogs
            .filter { it.type == "restock_order" && it.status in listOf("pending", "ordered") }
            .mapNotNull { it.itemId }
            .toSet()

        val candidates = snapshot.lowStockItems.filter { it.id !in pendingItemIds }
        if (candidates.isEmpty()) {
            _actionMessage.value = "All low-stock items already have active restock orders."
            return
        }

        viewModelScope.launch {
            var count = 0
            for (item in candidates) {
                val target = if (item.maxThreshold > item.minThreshold) {
                    item.maxThreshold
                } else {
                    (item.minThreshold * 2.0).coerceAtLeast(item.minThreshold + 2.0)
                }
                val orderQty = (target - item.quantity).coerceAtLeast(1.0)
                repository.createRestockOrder(
                    userId = currentUserId,
                    storeId = item.storeId,
                    item = item,
                    orderQty = orderQty,
                    reason = "Auto-replenishment (below min par ${item.minThreshold})"
                ).onSuccess { count++ }
            }
            _actionMessage.value = "Generated $count automated restock requests."
        }
    }

    fun advanceOrderStatus(log: ActivityLog, nextStatus: String) {
        val snapshot = (workspaceState.value as? DataUiState.Success)?.data ?: return
        val matchedItem = log.itemId?.let { id -> snapshot.allItems.firstOrNull { it.id == id } }
        viewModelScope.launch {
            repository.updateRestockOrderStatus(currentUserId, log, nextStatus, matchedItem)
                .onSuccess {
                    _actionMessage.value = if (nextStatus == "received") {
                        "Order received & stock replenished."
                    } else {
                        "Order marked as $nextStatus."
                    }
                }
                .onFailure {
                    _actionMessage.value = it.localizedMessage ?: "Failed to update order status."
                }
        }
    }

    private companion object {
        const val TAG = "StockLensVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
