package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserPersona(
    val key: String,
    val title: String,
    val subtitle: String,
    val defaultStoreName: String,
    val defaultStoreType: String
) {
    PANTRY(
        key = "pantry",
        title = "Home Pantry",
        subtitle = "Scan receipts & fridges, get expiry reminders",
        defaultStoreName = "Home Pantry",
        defaultStoreType = "pantry"
    ),
    CHEF(
        key = "chef",
        title = "Chef Kitchen",
        subtitle = "Monitor ingredient stock & reduce prep waste",
        defaultStoreName = "Main Kitchen",
        defaultStoreType = "kitchen"
    ),
    STORE(
        key = "store",
        title = "Store Owner",
        subtitle = "Automate restock orders & prevent overstock",
        defaultStoreName = "Flagship Store",
        defaultStoreType = "retail"
    );

    companion object {
        fun fromKey(key: String?): UserPersona =
            entries.firstOrNull { it.key == key } ?: PANTRY
    }
}

enum class PlanTier(val key: String, val label: String) {
    FREE("free", "Free"),
    PREMIUM("premium", "Premium");

    companion object {
        fun fromKey(key: String?): PlanTier =
            entries.firstOrNull { it.key == key } ?: FREE
    }
}

enum class ScanType(
    val key: String,
    val title: String,
    val subtitle: String
) {
    RECEIPT(
        key = "receipt",
        title = "Receipt",
        subtitle = "Add purchased items, prices & shelf life"
    ),
    FRIDGE(
        key = "fridge",
        title = "Fridge",
        subtitle = "Detect visible perishables & freshness"
    ),
    STOCK(
        key = "stock",
        title = "Stock Shelf",
        subtitle = "Count shelf units & detect low/overstock"
    );

    companion object {
        fun fromKey(key: String?): ScanType =
            entries.firstOrNull { it.key == key } ?: RECEIPT
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val persona: String = UserPersona.PANTRY.key,
    val tier: String = PlanTier.FREE.key,
    val activeStoreId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StoreLocation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val type: String = "pantry",
    val address: String? = null,
    val isDefault: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val storeId: String = "",
    val name: String = "",
    val category: String = "Pantry",
    val quantity: Double = 0.0,
    val unit: String = "pcs",
    val minThreshold: Double = 1.0,
    val maxThreshold: Double = 0.0,
    val unitCost: Double = 0.0,
    val expiryEpochMs: Long = 0L,
    val lastScanType: String = "manual",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isLowStock: Boolean
        get() = quantity <= minThreshold

    val isOverstocked: Boolean
        get() = maxThreshold > 0.0 && quantity > maxThreshold

    val totalValue: Double
        get() = quantity * unitCost

    fun daysUntilExpiry(nowMs: Long = System.currentTimeMillis()): Int? {
        if (expiryEpochMs <= 0L) return null
        val diff = expiryEpochMs - nowMs
        return kotlin.math.ceil(diff / 86_400_000.0).toInt()
    }
}

@IgnoreExtraProperties
data class ActivityLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val storeId: String = "",
    val itemId: String? = null,
    val itemName: String = "",
    val type: String = "adjustment",
    val quantityDelta: Double = 0.0,
    val unit: String = "pcs",
    val estimatedValue: Double = 0.0,
    val reason: String? = null,
    val status: String = "logged",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class ScannedItemCandidate(
    val tempId: String,
    val name: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val unitCost: Double,
    val estimatedShelfLifeDays: Int,
    val suggestedMinThreshold: Double,
    val actionHint: String,
    val matchedExistingItemId: String? = null,
    val selected: Boolean = true
)

data class ScanAnalysisResult(
    val scanType: ScanType,
    val detectedSourceLabel: String,
    val summary: String,
    val items: List<ScannedItemCandidate>
)
