package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.ActivityLog
import com.example.model.InventoryItem
import com.example.model.PlanTier
import com.example.model.ScanType
import com.example.model.ScannedItemCandidate
import com.example.model.StoreLocation
import com.example.model.UserPersona
import com.example.model.UserProfile
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class InventoryRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(userId: String) = db.collection("users").document(userId)
    private fun storesCol(userId: String) = userDoc(userId).collection("stores")
    private fun itemsCol(userId: String) = userDoc(userId).collection("items")
    private fun logsCol(userId: String) = userDoc(userId).collection("activity_logs")

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        userDoc(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.GET, "users/$userId")
                throw error
            }

    fun observeStores(userId: String): Flow<List<StoreLocation>> =
        storesCol(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StoreLocation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(compareByDescending<StoreLocation> { it.isDefault }.thenBy { it.name })
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "users/$userId/stores")
                throw error
            }

    fun observeItems(userId: String): Flow<List<InventoryItem>> =
        itemsCol(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    InventoryItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.name.lowercase() }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "users/$userId/items")
                throw error
            }

    fun observeActivityLogs(userId: String): Flow<List<ActivityLog>> =
        logsCol(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ActivityLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "users/$userId/activity_logs")
                throw error
            }

    suspend fun ensureInitialWorkspace(
        userId: String,
        displayName: String,
        email: String?
    ): Result<Unit> = runCatching {
        val profileRef = userDoc(userId)
        val existingProfile = try {
            profileRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, profileRef.path)
            throw e
        }

        if (!existingProfile.exists()) {
            val defaultStoreId = "default_store"
            val storeRef = storesCol(userId).document(defaultStoreId)
            val cleanName = displayName.ifBlank { "StockLens User" }.take(80)

            val storePayload = mapOf(
                "userId" to userId,
                "name" to UserPersona.PANTRY.defaultStoreName,
                "type" to UserPersona.PANTRY.defaultStoreType,
                "isDefault" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val profilePayload = buildMap<String, Any> {
                put("userId", userId)
                put("displayName", cleanName)
                if (!email.isNullOrBlank()) put("email", email.take(120))
                put("persona", UserPersona.PANTRY.key)
                put("tier", PlanTier.FREE.key)
                put("activeStoreId", defaultStoreId)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            try {
                db.runBatch { batch ->
                    batch.set(storeRef, storePayload)
                    batch.set(profileRef, profilePayload)
                }.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, profileRef.path)
                throw e
            }
        }
    }

    suspend fun updateUserSettings(
        userId: String,
        persona: UserPersona? = null,
        tier: PlanTier? = null,
        activeStoreId: String? = null
    ): Result<Unit> = runCatching {
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (persona != null) updates["persona"] = persona.key
        if (tier != null) updates["tier"] = tier.key
        if (!activeStoreId.isNullOrBlank()) updates["activeStoreId"] = activeStoreId

        val ref = userDoc(userId)
        try {
            ref.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun createStore(
        userId: String,
        name: String,
        type: String,
        address: String?
    ): Result<String> = runCatching {
        val cleanName = name.trim().take(60)
        require(cleanName.isNotEmpty()) { "Store name cannot be empty" }
        val storeId = "store_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val ref = storesCol(userId).document(storeId)
        val payload = buildMap<String, Any> {
            put("userId", userId)
            put("name", cleanName)
            put("type", type)
            if (!address.isNullOrBlank()) put("address", address.trim().take(120))
            put("isDefault", false)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            ref.set(payload).await()
            storeId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun deleteStore(userId: String, storeId: String): Result<Unit> = runCatching {
        val ref = storesCol(userId).document(storeId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    suspend fun upsertItem(userId: String, item: InventoryItem): Result<String> = runCatching {
        val cleanName = item.name.trim().take(80)
        require(cleanName.isNotEmpty()) { "Item name cannot be empty" }
        val cleanCategory = item.category.trim().ifBlank { "Pantry" }.take(40)
        val cleanUnit = item.unit.trim().ifBlank { "pcs" }.take(20)
        val qty = item.quantity.coerceIn(0.0, 1_000_000.0)
        val minT = item.minThreshold.coerceIn(0.0, 1_000_000.0)
        val maxT = item.maxThreshold.coerceIn(0.0, 1_000_000.0)
        val cost = item.unitCost.coerceIn(0.0, 1_000_000.0)
        val expiry = item.expiryEpochMs.coerceAtLeast(0L)

        if (item.id.isBlank()) {
            val itemId = "item_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val ref = itemsCol(userId).document(itemId)
            val payload = mapOf(
                "userId" to userId,
                "storeId" to item.storeId,
                "name" to cleanName,
                "category" to cleanCategory,
                "quantity" to qty,
                "unit" to cleanUnit,
                "minThreshold" to minT,
                "maxThreshold" to maxT,
                "unitCost" to cost,
                "expiryEpochMs" to expiry,
                "lastScanType" to item.lastScanType,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                ref.set(payload).await()
                itemId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, ref.path)
                throw e
            }
        } else {
            val ref = itemsCol(userId).document(item.id)
            val updates = mapOf(
                "name" to cleanName,
                "category" to cleanCategory,
                "quantity" to qty,
                "unit" to cleanUnit,
                "minThreshold" to minT,
                "maxThreshold" to maxT,
                "unitCost" to cost,
                "expiryEpochMs" to expiry,
                "lastScanType" to item.lastScanType,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                ref.update(updates).await()
                item.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, ref.path)
                throw e
            }
        }
    }

    suspend fun adjustItemQuantity(
        userId: String,
        item: InventoryItem,
        delta: Double,
        logType: String,
        reason: String?
    ): Result<Unit> = runCatching {
        val newQty = (item.quantity + delta).coerceIn(0.0, 1_000_000.0)
        val itemRef = itemsCol(userId).document(item.id)
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val logRef = logsCol(userId).document(logId)

        val absQty = kotlin.math.abs(delta)
        val estValue = (absQty * item.unitCost).coerceAtLeast(0.0)

        val logPayload = buildMap<String, Any> {
            put("userId", userId)
            put("storeId", item.storeId)
            put("itemId", item.id)
            put("itemName", item.name.take(80))
            put("type", logType)
            put("quantityDelta", delta)
            put("unit", item.unit.take(20))
            put("estimatedValue", estValue)
            if (!reason.isNullOrBlank()) put("reason", reason.trim().take(160))
            put("status", "logged")
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        try {
            db.runBatch { batch ->
                batch.update(
                    itemRef,
                    mapOf(
                        "quantity" to newQty,
                        "lastScanType" to item.lastScanType,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(logRef, logPayload)
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, itemRef.path)
            throw e
        }
    }

    suspend fun deleteItem(userId: String, itemId: String): Result<Unit> = runCatching {
        val ref = itemsCol(userId).document(itemId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    suspend fun applyScanResults(
        userId: String,
        storeId: String,
        scanType: ScanType,
        candidates: List<ScannedItemCandidate>,
        existingItems: List<InventoryItem>
    ): Result<Int> = runCatching {
        val selected = candidates.filter { it.selected && it.name.isNotBlank() }
        if (selected.isEmpty()) return@runCatching 0

        val nowMs = System.currentTimeMillis()
        val storeItems = existingItems.filter { it.storeId == storeId }

        db.runBatch { batch ->
            for (candidate in selected) {
                val match = candidate.matchedExistingItemId?.let { id ->
                    storeItems.firstOrNull { it.id == id }
                } ?: storeItems.firstOrNull {
                    it.name.equals(candidate.name.trim(), ignoreCase = true)
                }

                val expiryMs = if (candidate.estimatedShelfLifeDays > 0) {
                    nowMs + candidate.estimatedShelfLifeDays * 86_400_000L
                } else {
                    0L
                }

                if (match != null) {
                    // Receipt adds to existing stock; Fridge/Stock replaces observed stock level
                    val updatedQty = if (scanType == ScanType.RECEIPT) {
                        (match.quantity + candidate.quantity).coerceIn(0.0, 1_000_000.0)
                    } else {
                        candidate.quantity.coerceIn(0.0, 1_000_000.0)
                    }
                    val itemRef = itemsCol(userId).document(match.id)
                    batch.update(
                        itemRef,
                        mapOf(
                            "quantity" to updatedQty,
                            "lastScanType" to scanType.key,
                            "expiryEpochMs" to if (expiryMs > 0L) expiryMs else match.expiryEpochMs,
                            "unitCost" to if (candidate.unitCost > 0.0) candidate.unitCost else match.unitCost,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                    val delta = if (scanType == ScanType.RECEIPT) candidate.quantity else (updatedQty - match.quantity)
                    batch.set(
                        logsCol(userId).document(logId),
                        mapOf(
                            "userId" to userId,
                            "storeId" to storeId,
                            "itemId" to match.id,
                            "itemName" to match.name.take(80),
                            "type" to "scan_in",
                            "quantityDelta" to delta,
                            "unit" to match.unit.take(20),
                            "estimatedValue" to (kotlin.math.abs(delta) * (if (candidate.unitCost > 0.0) candidate.unitCost else match.unitCost)),
                            "reason" to "${scanType.title} AI Scan",
                            "status" to "logged",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val itemId = "item_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                    val itemRef = itemsCol(userId).document(itemId)
                    val cleanName = candidate.name.trim().take(80)
                    val cleanUnit = candidate.unit.trim().ifBlank { "pcs" }.take(20)
                    val cleanCategory = candidate.category.trim().ifBlank { "Pantry" }.take(40)
                    val qty = candidate.quantity.coerceIn(0.0, 1_000_000.0)
                    val cost = candidate.unitCost.coerceIn(0.0, 1_000_000.0)
                    val minT = candidate.suggestedMinThreshold.coerceIn(0.0, 1_000_000.0)

                    batch.set(
                        itemRef,
                        mapOf(
                            "userId" to userId,
                            "storeId" to storeId,
                            "name" to cleanName,
                            "category" to cleanCategory,
                            "quantity" to qty,
                            "unit" to cleanUnit,
                            "minThreshold" to minT,
                            "maxThreshold" to 0.0,
                            "unitCost" to cost,
                            "expiryEpochMs" to expiryMs,
                            "lastScanType" to scanType.key,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                    batch.set(
                        logsCol(userId).document(logId),
                        mapOf(
                            "userId" to userId,
                            "storeId" to storeId,
                            "itemId" to itemId,
                            "itemName" to cleanName,
                            "type" to "scan_in",
                            "quantityDelta" to qty,
                            "unit" to cleanUnit,
                            "estimatedValue" to (qty * cost),
                            "reason" to "${scanType.title} AI Scan",
                            "status" to "logged",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
        }.await()

        selected.size
    }.onFailure { error ->
        if (error is CancellationException) throw error
        handleFirestoreError(error, OperationType.WRITE, "users/$userId/items")
    }

    suspend fun createRestockOrder(
        userId: String,
        storeId: String,
        item: InventoryItem,
        orderQty: Double,
        reason: String
    ): Result<String> = runCatching {
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val ref = logsCol(userId).document(logId)
        val qty = orderQty.coerceIn(0.1, 1_000_000.0)
        val payload = mapOf(
            "userId" to userId,
            "storeId" to storeId,
            "itemId" to item.id,
            "itemName" to item.name.take(80),
            "type" to "restock_order",
            "quantityDelta" to qty,
            "unit" to item.unit.take(20),
            "estimatedValue" to (qty * item.unitCost),
            "reason" to reason.trim().take(160),
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            ref.set(payload).await()
            logId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun updateRestockOrderStatus(
        userId: String,
        log: ActivityLog,
        newStatus: String,
        currentItem: InventoryItem?
    ): Result<Unit> = runCatching {
        val logRef = logsCol(userId).document(log.id)
        try {
            db.runBatch { batch ->
                batch.update(
                    logRef,
                    mapOf(
                        "status" to newStatus,
                        "reason" to (log.reason ?: "Restock Order"),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (newStatus == "received" && currentItem != null) {
                    val itemRef = itemsCol(userId).document(currentItem.id)
                    batch.update(
                        itemRef,
                        mapOf(
                            "quantity" to (currentItem.quantity + log.quantityDelta).coerceIn(0.0, 1_000_000.0),
                            "lastScanType" to "manual",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, logRef.path)
            throw e
        }
    }

    private fun unwrapAndLog(error: Throwable, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
