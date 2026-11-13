package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.InventoryItem
import com.example.model.PlanTier
import com.example.model.UserPersona
import com.example.ui.components.AddEditItemDialog
import com.example.ui.components.LogWasteDialog
import com.example.ui.components.RestockOrderDialog
import com.example.ui.components.formatDecimal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PremiumGold
import com.example.ui.theme.StatusCritical
import com.example.ui.theme.StatusHealthy
import com.example.ui.theme.StatusOverstock
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.InventoryFilter
import com.example.ui.viewmodel.WorkspaceSnapshot
import java.util.Locale

@Composable
fun InventoryScreen(
    snapshot: WorkspaceSnapshot,
    searchQuery: String,
    selectedFilter: InventoryFilter,
    selectedCategory: String,
    onSearchChange: (String) -> Unit,
    onFilterChange: (InventoryFilter) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSwitchStore: (String) -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onSaveItem: (InventoryItem) -> Unit,
    onAdjustStock: (InventoryItem, Double) -> Unit,
    onLogWaste: (InventoryItem, Double, String) -> Unit,
    onRequestRestock: (InventoryItem, Double, String) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var wasteTarget by remember { mutableStateOf<InventoryItem?>(null) }
    var restockTarget by remember { mutableStateOf<InventoryItem?>(null) }
    var storeDropdownExpanded by remember { mutableStateOf(false) }

    val categories = remember(snapshot.storeItems) {
        listOf("All") + snapshot.storeItems.map { it.category }.distinct().sorted()
    }

    val filteredItems = remember(snapshot.storeItems, searchQuery, selectedFilter, selectedCategory) {
        val nowMs = System.currentTimeMillis()
        snapshot.storeItems.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || item.category == selectedCategory
            val matchesFilter = when (selectedFilter) {
                InventoryFilter.ALL -> true
                InventoryFilter.LOW_STOCK -> item.isLowStock
                InventoryFilter.EXPIRING -> {
                    val d = item.daysUntilExpiry(nowMs)
                    d != null && d <= 3
                }
                InventoryFilter.OVERSTOCK -> item.isOverstocked
            }
            matchesQuery && matchesCategory && matchesFilter
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_item_fab")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add inventory item manually")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Workspace Header: Store Switcher + Persona & Plan Badges
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { storeDropdownExpanded = true }
                                .testTag("store_switcher_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Storefront,
                                    contentDescription = "Active store",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = snapshot.activeStore?.name ?: "My Workspace",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${snapshot.persona.title} • ${snapshot.tier.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.ExpandMore,
                                    contentDescription = "Switch location",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = storeDropdownExpanded,
                            onDismissRequest = { storeDropdownExpanded = false }
                        ) {
                            snapshot.stores.forEach { store ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (store.id == snapshot.activeStore?.id) {
                                                "• ${store.name}"
                                            } else {
                                                store.name
                                            }
                                        )
                                    },
                                    onClick = {
                                        storeDropdownExpanded = false
                                        onSwitchStore(store.id)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (snapshot.tier == PlanTier.PREMIUM) {
                                            "+ Manage Locations"
                                        } else {
                                            "Unlock Multi-Store (Premium)"
                                        },
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.WorkspacePremium,
                                        contentDescription = null,
                                        tint = PremiumGold
                                    )
                                },
                                onClick = {
                                    storeDropdownExpanded = false
                                    onNavigateToProfile()
                                }
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onNavigateToScan,
                        modifier = Modifier.testTag("header_scan_button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = "Scan photo",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Photo")
                    }
                }
            }

            // Persona-tailored KPI Summary Card
            item {
                PersonaKpiBanner(
                    snapshot = snapshot,
                    onFilterClick = onFilterChange,
                    onOpenInsights = onNavigateToInsights
                )
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search items, ingredients, categories...") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inventory_search_input")
                )
            }

            // Filter Chips + Category Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InventoryFilter.entries.forEach { filter ->
                            val count = when (filter) {
                                InventoryFilter.ALL -> snapshot.storeItems.size
                                InventoryFilter.LOW_STOCK -> snapshot.lowStockItems.size
                                InventoryFilter.EXPIRING -> snapshot.expiringItems.size
                                InventoryFilter.OVERSTOCK -> snapshot.overstockItems.size
                            }
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { onFilterChange(filter) },
                                label = { Text("${filter.label} ($count)") },
                                modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                            )
                        }
                    }

                    if (categories.size > 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                AssistChip(
                                    onClick = { onCategoryChange(cat) },
                                    label = { Text(cat) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (selectedCategory == cat) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Empty State or Item Cards
            if (filteredItems.isEmpty()) {
                item {
                    EmptyInventoryCard(
                        hasAnyItems = snapshot.storeItems.isNotEmpty(),
                        onScanPhoto = onNavigateToScan,
                        onAddManual = {
                            editingItem = null
                            showAddEditDialog = true
                        }
                    )
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    InventoryItemCard(
                        item = item,
                        persona = snapshot.persona,
                        onIncrement = { onAdjustStock(item, 1.0) },
                        onDecrement = { onAdjustStock(item, -1.0) },
                        onEdit = {
                            editingItem = item
                            showAddEditDialog = true
                        },
                        onLogWaste = { wasteTarget = item },
                        onRequestRestock = { restockTarget = item },
                        onDelete = { onDeleteItem(item.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(76.dp)) }
        }
    }

    if (showAddEditDialog) {
        AddEditItemDialog(
            initialItem = editingItem,
            storeId = snapshot.activeStore?.id.orEmpty(),
            onDismiss = { showAddEditDialog = false },
            onSave = { item ->
                onSaveItem(item)
                showAddEditDialog = false
            }
        )
    }

    wasteTarget?.let { target ->
        LogWasteDialog(
            item = target,
            onDismiss = { wasteTarget = null },
            onConfirmWaste = { qty, reason ->
                onLogWaste(target, qty, reason)
                wasteTarget = null
            }
        )
    }

    restockTarget?.let { target ->
        RestockOrderDialog(
            item = target,
            onDismiss = { restockTarget = null },
            onConfirmOrder = { qty, reason ->
                onRequestRestock(target, qty, reason)
                restockTarget = null
            }
        )
    }
}

@Composable
private fun PersonaKpiBanner(
    snapshot: WorkspaceSnapshot,
    onFilterClick: (InventoryFilter) -> Unit,
    onOpenInsights: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (snapshot.persona) {
                            UserPersona.PANTRY -> "Pantry Freshness Pulse"
                            UserPersona.CHEF -> "Kitchen Prep & Waste Pulse"
                            UserPersona.STORE -> "Store Stock & Par Health"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = snapshot.persona.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = String.format(Locale.US, "$%.0f", snapshot.totalStoreValue),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiPill(
                    label = "Items",
                    value = snapshot.storeItems.size.toString(),
                    modifier = Modifier.weight(1f),
                    onClick = { onFilterClick(InventoryFilter.ALL) }
                )
                KpiPill(
                    label = if (snapshot.persona == UserPersona.STORE) "Restock" else "Expiring",
                    value = if (snapshot.persona == UserPersona.STORE) {
                        snapshot.lowStockItems.size.toString()
                    } else {
                        snapshot.expiringItems.size.toString()
                    },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onFilterClick(
                            if (snapshot.persona == UserPersona.STORE) {
                                InventoryFilter.LOW_STOCK
                            } else {
                                InventoryFilter.EXPIRING
                            }
                        )
                    }
                )
                KpiPill(
                    label = when (snapshot.persona) {
                        UserPersona.PANTRY -> "Low Stock"
                        UserPersona.CHEF -> "Waste $"
                        UserPersona.STORE -> "Overstock"
                    },
                    value = when (snapshot.persona) {
                        UserPersona.PANTRY -> snapshot.lowStockItems.size.toString()
                        UserPersona.CHEF -> String.format(Locale.US, "$%.0f", snapshot.totalWasteCost)
                        UserPersona.STORE -> snapshot.overstockItems.size.toString()
                    },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when (snapshot.persona) {
                            UserPersona.PANTRY -> onFilterClick(InventoryFilter.LOW_STOCK)
                            UserPersona.CHEF -> onOpenInsights()
                            UserPersona.STORE -> onFilterClick(InventoryFilter.OVERSTOCK)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun KpiPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun InventoryItemCard(
    item: InventoryItem,
    persona: UserPersona,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEdit: () -> Unit,
    onLogWaste: () -> Unit,
    onRequestRestock: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val daysLeft = item.daysUntilExpiry()

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("inventory_item_card_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(item.category)
                            if (item.unitCost > 0.0) {
                                append(" • ")
                                append(String.format(Locale.US, "$%.2f/%s", item.unitCost, item.unit))
                            }
                            append(" • Min ${formatDecimal(item.minThreshold)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Item actions for ${item.name}")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit details") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Log waste / spoilage") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onLogWaste()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Request restock") },
                            leadingIcon = { Icon(Icons.Outlined.LocalShipping, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRequestRestock()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete item", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            // Status Badges + Quantity Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.isLowStock) {
                        StatusBadge("LOW STOCK", StatusCritical)
                    } else if (item.isOverstocked) {
                        StatusBadge("OVERSTOCK", StatusOverstock)
                    }
                    if (daysLeft != null && daysLeft <= 3) {
                        val label = when {
                            daysLeft < 0 -> "EXPIRED"
                            daysLeft == 0 -> "EXPIRES TODAY"
                            else -> "EXPIRES ${daysLeft}D"
                        }
                        StatusBadge(label, if (daysLeft <= 0) StatusCritical else StatusWarning)
                    } else if (!item.isLowStock && !item.isOverstocked) {
                        StatusBadge("IN STOCK", StatusHealthy)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onDecrement,
                        enabled = item.quantity > 0.0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Remove,
                            contentDescription = "Decrease ${item.name} quantity",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "${formatDecimal(item.quantity)} ${item.unit}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    FilledTonalIconButton(
                        onClick = onIncrement,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "Increase ${item.name} quantity",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Contextual 1-Tap Action Strip for Urgent Scenarios
            AnimatedVisibility(visible = item.isLowStock || (daysLeft != null && daysLeft <= 2)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.isLowStock) {
                        OutlinedButton(
                            onClick = onRequestRestock,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp)
                        ) {
                            Icon(
                                Icons.Outlined.LocalShipping,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (persona == UserPersona.STORE) "Trigger Restock" else "Add to Restock",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    if (daysLeft != null && daysLeft <= 2) {
                        OutlinedButton(
                            onClick = onLogWaste,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp)
                        ) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Log Spoilage", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.13f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyInventoryCard(
    hasAnyItems: Boolean,
    onScanPhoto: () -> Unit,
    onAddManual: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_inventory),
                contentDescription = "Empty shelf ready to scan",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Text(
                text = if (hasAnyItems) "No matching items" else "Your digital inventory is ready",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = if (hasAnyItems) {
                    "Try clearing your search or alert filters."
                } else {
                    "Snap a receipt, open fridge, or stock shelf photo and StockLens will automatically populate your database."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onScanPhoto,
                    modifier = Modifier.weight(1f).testTag("empty_scan_cta")
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Photo")
                }
                OutlinedButton(
                    onClick = onAddManual,
                    modifier = Modifier.weight(1f).testTag("empty_manual_cta")
                ) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Item")
                }
            }
        }
    }
}
