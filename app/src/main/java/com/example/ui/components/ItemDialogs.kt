package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.InventoryItem

@Composable
fun AddEditItemDialog(
    initialItem: InventoryItem?,
    storeId: String,
    onDismiss: () -> Unit,
    onSave: (InventoryItem) -> Unit
) {
    var name by remember { mutableStateOf(initialItem?.name.orEmpty()) }
    var category by remember { mutableStateOf(initialItem?.category ?: "Pantry") }
    var quantityText by remember {
        mutableStateOf(
            if (initialItem != null) formatDecimal(initialItem.quantity) else "1"
        )
    }
    var unit by remember { mutableStateOf(initialItem?.unit ?: "pcs") }
    var minThresholdText by remember {
        mutableStateOf(
            if (initialItem != null) formatDecimal(initialItem.minThreshold) else "1"
        )
    }
    var maxThresholdText by remember {
        mutableStateOf(
            if (initialItem != null && initialItem.maxThreshold > 0) formatDecimal(initialItem.maxThreshold) else "0"
        )
    }
    var unitCostText by remember {
        mutableStateOf(
            if (initialItem != null && initialItem.unitCost > 0) formatDecimal(initialItem.unitCost) else "0"
        )
    }
    var shelfLifeDaysText by remember {
        val existingDays = initialItem?.daysUntilExpiry()?.coerceAtLeast(0)?.toString() ?: "0"
        mutableStateOf(existingDays)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialItem == null) "Add Inventory Item" else "Edit Item",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("item_name_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Quantity") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitCostText,
                        onValueChange = { unitCostText = it },
                        label = { Text("Unit Cost ($)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = minThresholdText,
                        onValueChange = { minThresholdText = it },
                        label = { Text("Min Par") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxThresholdText,
                        onValueChange = { maxThresholdText = it },
                        label = { Text("Max Par (0=off)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = shelfLifeDaysText,
                    onValueChange = { shelfLifeDaysText = it },
                    label = { Text("Days Until Expiry (0 = non-perishable)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1.0
                    val minT = minThresholdText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1.0
                    val maxT = maxThresholdText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val cost = unitCostText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val days = shelfLifeDaysText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val expiryMs = if (days > 0) {
                        System.currentTimeMillis() + days * 86_400_000L
                    } else {
                        0L
                    }
                    onSave(
                        (initialItem ?: InventoryItem()).copy(
                            storeId = initialItem?.storeId?.ifBlank { storeId } ?: storeId,
                            name = name.trim(),
                            category = category.trim().ifBlank { "Pantry" },
                            quantity = qty,
                            unit = unit.trim().ifBlank { "pcs" },
                            minThreshold = minT,
                            maxThreshold = maxT,
                            unitCost = cost,
                            expiryEpochMs = expiryMs,
                            lastScanType = initialItem?.lastScanType ?: "manual"
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_item_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LogWasteDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onConfirmWaste: (qty: Double, reason: String) -> Unit
) {
    var qtyText by remember { mutableStateOf(formatDecimal(item.quantity.coerceAtMost(1.0))) }
    val reasons = listOf("Spoiled / Expired", "Prep Trim", "Damaged", "Overcooked")
    var selectedReason by remember { mutableStateOf(reasons.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Waste — ${item.name}", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Available: ${formatDecimal(item.quantity)} ${item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Wasted Quantity (${item.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Reason", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    reasons.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { reason ->
                                FilterChip(
                                    selected = selectedReason == reason,
                                    onClick = { selectedReason = reason },
                                    label = { Text(reason, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyText.toDoubleOrNull()?.coerceAtLeast(0.1) ?: 1.0
                    onConfirmWaste(qty, selectedReason)
                }
            ) {
                Text("Log Waste")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RestockOrderDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onConfirmOrder: (qty: Double, reason: String) -> Unit
) {
    val suggested = if (item.maxThreshold > item.quantity) {
        item.maxThreshold - item.quantity
    } else {
        (item.minThreshold * 2.0 - item.quantity).coerceAtLeast(2.0)
    }
    var qtyText by remember { mutableStateOf(formatDecimal(suggested)) }
    var note by remember { mutableStateOf("Below min par (${formatDecimal(item.minThreshold)} ${item.unit})") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restock — ${item.name}", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current: ${formatDecimal(item.quantity)} ${item.unit} • Min Par: ${formatDecimal(item.minThreshold)} ${item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Order Quantity (${item.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Supplier / Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyText.toDoubleOrNull()?.coerceAtLeast(0.5) ?: 1.0
                    onConfirmOrder(qty, note.ifBlank { "Manual restock request" })
                }
            ) {
                Text("Create Order")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddStoreDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: String, address: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val types = listOf("pantry" to "Pantry", "kitchen" to "Kitchen", "retail" to "Retail Store", "warehouse" to "Warehouse")
    var selectedType by remember { mutableStateOf("retail") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Store / Location", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Location Name (e.g. Downtown Bistro)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store_name_input")
                )
                Text("Workspace Type", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    types.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (key, label) ->
                                FilterChip(
                                    selected = selectedType == key,
                                    onClick = { selectedType = key },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address or Room (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim(), selectedType, address.trim().ifBlank { null }) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_store_button")
            ) {
                Text("Create Location")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatDecimal(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format("%.1f", value)
    }
}
