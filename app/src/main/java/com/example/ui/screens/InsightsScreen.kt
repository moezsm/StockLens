package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ActivityLog
import com.example.model.InventoryItem
import com.example.model.PlanTier
import com.example.ui.components.formatDecimal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PremiumGold
import com.example.ui.theme.StatusCritical
import com.example.ui.theme.StatusHealthy
import com.example.ui.theme.StatusOverstock
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.WorkspaceSnapshot
import java.util.Locale

private enum class InsightsTab(val label: String) {
    ALERTS_ORDERS("Alerts & Restock"),
    WASTE_USAGE("Waste & Usage"),
    ANALYTICS("Analytics")
}

@Composable
fun InsightsScreen(
    snapshot: WorkspaceSnapshot,
    onAutoGenerateRestock: () -> Unit,
    onRequestSingleRestock: (InventoryItem) -> Unit,
    onAdvanceOrderStatus: (ActivityLog, String) -> Unit,
    onUpgradeToPremium: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(InsightsTab.ALERTS_ORDERS) }

    val restockOrders = remember(snapshot.storeLogs) {
        snapshot.storeLogs.filter { it.type == "restock_order" }
    }
    val wasteLogs = remember(snapshot.storeLogs) {
        snapshot.storeLogs.filter { it.type == "waste" }
    }
    val usageLogs = remember(snapshot.storeLogs) {
        snapshot.storeLogs.filter { it.type in listOf("consumed", "adjustment") }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Alerts, Waste & Insights",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "${snapshot.activeStore?.name ?: "Workspace"} • ${snapshot.persona.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InsightsTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) },
                        leadingIcon = if (tab == InsightsTab.ANALYTICS && snapshot.tier == PlanTier.FREE) {
                            {
                                Icon(
                                    Icons.Outlined.WorkspacePremium,
                                    contentDescription = "Premium",
                                    tint = PremiumGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("insights_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }

        when (selectedTab) {
            InsightsTab.ALERTS_ORDERS -> {
                // Expiring Items Alert (User A & User B)
                item {
                    SectionHeader(
                        title = "Expiration Reminders (${snapshot.expiringItems.size})",
                        subtitle = "Items expiring within 3 days or already expired"
                    )
                }
                if (snapshot.expiringItems.isEmpty()) {
                    item {
                        QuietEmptyBanner("No items expiring soon. Everything is fresh!")
                    }
                } else {
                    items(snapshot.expiringItems, key = { "exp_${it.id}" }) { item ->
                        val days = item.daysUntilExpiry() ?: 0
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    contentDescription = "Expiry alert",
                                    tint = if (days <= 0) StatusCritical else StatusWarning
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${formatDecimal(item.quantity)} ${item.unit} • " +
                                            when {
                                                days < 0 -> "Expired ${-days}d ago"
                                                days == 0 -> "Expires today"
                                                else -> "Expires in $days day(s)"
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (days <= 0) StatusCritical else StatusWarning
                                    )
                                }
                            }
                        }
                    }
                }

                // Low Stock + Automated Restock Action (User C)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            title = "Low Stock Thresholds (${snapshot.lowStockItems.size})",
                            subtitle = "Trigger automated replenishment orders",
                            modifier = Modifier.weight(1f)
                        )
                        if (snapshot.lowStockItems.isNotEmpty()) {
                            Button(
                                onClick = onAutoGenerateRestock,
                                modifier = Modifier.testTag("auto_restock_button"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto-Order")
                            }
                        }
                    }
                }

                if (snapshot.lowStockItems.isEmpty()) {
                    item {
                        QuietEmptyBanner("All items are above their minimum par thresholds.")
                    }
                } else {
                    items(snapshot.lowStockItems, key = { "low_${it.id}" }) { item ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "On hand: ${formatDecimal(item.quantity)} ${item.unit} (Min: ${formatDecimal(item.minThreshold)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusCritical
                                    )
                                }
                                OutlinedButton(onClick = { onRequestSingleRestock(item) }) {
                                    Text("Restock")
                                }
                            }
                        }
                    }
                }

                // Active Restock Orders
                item {
                    SectionHeader(
                        title = "Restock Requests (${restockOrders.size})",
                        subtitle = "Track pending orders & mark received to update stock",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (restockOrders.isEmpty()) {
                    item {
                        QuietEmptyBanner("No restock orders created yet.")
                    }
                } else {
                    items(restockOrders, key = { it.id }) { order ->
                        RestockOrderCard(
                            order = order,
                            onAdvanceStatus = { next -> onAdvanceOrderStatus(order, next) }
                        )
                    }
                }
            }

            InsightsTab.WASTE_USAGE -> {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Waste Cost Tracked", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${wasteLogs.size} spoilage/trim incidents",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "$%.2f", snapshot.totalWasteCost),
                                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = StatusCritical
                            )
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Waste & Spoilage Log",
                        subtitle = "Chef & pantry waste tracking for loss prevention"
                    )
                }

                if (wasteLogs.isEmpty()) {
                    item {
                        QuietEmptyBanner("Zero waste logged! Tap the menu on any item to record spoilage or prep trim.")
                    }
                } else {
                    items(wasteLogs, key = { it.id }) { log ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteSweep,
                                    contentDescription = "Waste event",
                                    tint = StatusCritical
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(log.itemName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${log.reason ?: "Waste"} • ${formatDecimal(kotlin.math.abs(log.quantityDelta))} ${log.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "-$%.2f", log.estimatedValue),
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = StatusCritical
                                )
                            }
                        }
                    }
                }

                if (usageLogs.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Recent Consumption & Adjustments",
                            subtitle = "Latest manual & +/- stock movements",
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(usageLogs.take(10), key = { it.id }) { log ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(log.itemName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${if (log.quantityDelta >= 0) "+" else ""}${formatDecimal(log.quantityDelta)} ${log.unit}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = if (log.quantityDelta >= 0) StatusHealthy else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            InsightsTab.ANALYTICS -> {
                if (snapshot.tier == PlanTier.FREE) {
                    item {
                        PremiumAnalyticsUpsellCard(onUpgrade = onUpgradeToPremium)
                    }
                } else {
                    item {
                        PremiumAnalyticsContent(snapshot = snapshot)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestockOrderCard(
    order: ActivityLog,
    onAdvanceStatus: (String) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (order.status == "received") Icons.Outlined.CheckCircle else Icons.Outlined.LocalShipping,
                contentDescription = order.status,
                tint = if (order.status == "received") StatusHealthy else MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(order.itemName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "+${formatDecimal(order.quantityDelta)} ${order.unit} • ${order.status.uppercase()} • " +
                        String.format(Locale.US, "$%.2f", order.estimatedValue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (order.status) {
                "pending" -> OutlinedButton(onClick = { onAdvanceStatus("ordered") }) {
                    Text("Mark Ordered")
                }
                "ordered" -> Button(onClick = { onAdvanceStatus("received") }) {
                    Text("Receive")
                }
                else -> Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusHealthy.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "RECEIVED",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusHealthy,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumAnalyticsUpsellCard(onUpgrade: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PremiumGold.copy(alpha = 0.16f)
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "Premium feature",
                        tint = PremiumGold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column {
                    Text("Premium Advanced Analytics", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Multi-store valuation, overstock & waste insights",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Your Free profile stays 100% usable for single-location scanning, expiry alerts, waste logs, and restock orders. Upgrade to Premium to unlock:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("• Unlimited stores, kitchens & warehouses", style = MaterialTheme.typography.bodyMedium)
                Text("• Cross-store inventory value & par-health comparison", style = MaterialTheme.typography.bodyMedium)
                Text("• Category valuation charts & overstock ratio analytics", style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = onUpgrade,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("upsell_unlock_premium_button")
            ) {
                Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Switch to Premium Profile")
            }
        }
    }
}

@Composable
private fun PremiumAnalyticsContent(snapshot: WorkspaceSnapshot) {
    val totalAllValue = remember(snapshot.allItems) { snapshot.allItems.sumOf { it.totalValue } }
    val categoryTotals = remember(snapshot.allItems) {
        snapshot.allItems
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.totalValue.coerceAtLeast(it.quantity) } }
            .entries
            .sortedByDescending { it.value }
            .take(6)
    }
    val maxCategoryVal = (categoryTotals.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1.0)
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Cross-Store Summary KPIs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnalyticsMetricCard(
                label = "Total Portfolio",
                value = String.format(Locale.US, "$%.0f", totalAllValue),
                sublabel = "${snapshot.stores.size} location(s)",
                modifier = Modifier.weight(1f)
            )
            AnalyticsMetricCard(
                label = "Par Exceptions",
                value = (snapshot.allItems.count { it.isLowStock } + snapshot.allItems.count { it.isOverstocked }).toString(),
                sublabel = "${snapshot.allItems.count { it.isOverstocked }} overstocked",
                modifier = Modifier.weight(1f)
            )
        }

        // Category Value / Volume Bar Chart
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Category Distribution (All Locations)", style = MaterialTheme.typography.titleMedium)
                if (categoryTotals.isEmpty()) {
                    Text(
                        "Add or scan items to see category distribution.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    categoryTotals.forEach { (category, amount) ->
                        val fraction = (amount / maxCategoryVal).toFloat().coerceIn(0.05f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(category, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    String.format(Locale.US, "%.1f", amount),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                            ) {
                                drawRoundRect(
                                    color = trackColor,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                                drawRoundRect(
                                    color = primaryColor,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width * fraction, size.height),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Multi-Store Health Breakdown
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Multi-Store Stock Health", style = MaterialTheme.typography.titleMedium)
                snapshot.storeHealthSummaries.forEach { summary ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(summary.store.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${summary.itemCount} items • ${summary.lowStockCount} low • ${summary.overstockCount} overstock • ${summary.expiringCount} expiring",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "$%.0f", summary.totalValue),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsMetricCard(
    label: String,
    value: String,
    sublabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.primary
            )
            Text(sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuietEmptyBanner(text: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}
