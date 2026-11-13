package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.model.ScanType
import com.example.model.ScannedItemCandidate
import com.example.ui.components.formatDecimal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusHealthy
import com.example.ui.viewmodel.ScanUiState
import com.example.ui.viewmodel.WorkspaceSnapshot
import java.io.File
import java.util.Locale

@Composable
fun ScanScreen(
    snapshot: WorkspaceSnapshot,
    selectedScanType: ScanType,
    scanState: ScanUiState,
    onSelectScanType: (ScanType) -> Unit,
    onAnalyzeBitmap: (Bitmap, ScanType) -> Unit,
    onToggleCandidate: (String) -> Unit,
    onUpdateCandidateQty: (String, Double) -> Unit,
    onCommitScan: () -> Unit,
    onResetScan: () -> Unit,
    onDoneViewingInventory: () -> Unit
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            decodeBitmapFromUri(context, uri)?.let { bmp ->
                onAnalyzeBitmap(bmp, selectedScanType)
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            decodeBitmapFromUri(context, uri)?.let { bmp ->
                onAnalyzeBitmap(bmp, selectedScanType)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "AI Photo Stock Scanner",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Target workspace: ${snapshot.activeStore?.name ?: "Default"} (${snapshot.persona.title})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Scan Mode Cards: Receipt / Fridge / Stock Shelf
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScanModeCard(
                    type = ScanType.RECEIPT,
                    icon = Icons.Outlined.ReceiptLong,
                    selected = selectedScanType == ScanType.RECEIPT,
                    onClick = { onSelectScanType(ScanType.RECEIPT) },
                    modifier = Modifier.weight(1f)
                )
                ScanModeCard(
                    type = ScanType.FRIDGE,
                    icon = Icons.Outlined.Kitchen,
                    selected = selectedScanType == ScanType.FRIDGE,
                    onClick = { onSelectScanType(ScanType.FRIDGE) },
                    modifier = Modifier.weight(1f)
                )
                ScanModeCard(
                    type = ScanType.STOCK,
                    icon = Icons.Outlined.Inventory,
                    selected = selectedScanType == ScanType.STOCK,
                    onClick = { onSelectScanType(ScanType.STOCK) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Action State Machine
        when (scanState) {
            is ScanUiState.Idle -> {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Mode: ${selectedScanType.title}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = selectedScanType.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val uri = createTempCaptureUri(context)
                                        pendingCameraUri = uri
                                        takePictureLauncher.launch(uri)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("take_photo_button")
                                ) {
                                    Icon(
                                        Icons.Outlined.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Camera")
                                }
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("pick_photo_button")
                                ) {
                                    Icon(
                                        Icons.Outlined.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Gallery")
                                }
                            }
                        }
                    }
                }
            }

            is ScanUiState.Analyzing -> {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Image(
                                bitmap = scanState.previewBitmap.asImageBitmap(),
                                contentDescription = "Photo being analyzed",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(MaterialTheme.shapes.medium),
                                contentScale = ContentScale.Crop
                            )
                            CircularProgressIndicator()
                            Text(
                                text = "Analyzing ${scanState.scanType.title} with Gemini Vision...",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Extracting items, quantities, unit costs, and expiration estimates.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is ScanUiState.Review -> {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = scanState.result.detectedSourceLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = scanState.result.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(onClick = onResetScan) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retake")
                                }
                            }
                            Image(
                                bitmap = scanState.previewBitmap.asImageBitmap(),
                                contentDescription = "Scanned photo preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(MaterialTheme.shapes.small),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                items(scanState.result.items, key = { it.tempId }) { candidate ->
                    ScannedCandidateCard(
                        candidate = candidate,
                        scanType = scanState.result.scanType,
                        onToggle = { onToggleCandidate(candidate.tempId) },
                        onAdjustQty = { delta ->
                            onUpdateCandidateQty(candidate.tempId, candidate.quantity + delta)
                        }
                    )
                }

                item {
                    val selectedCount = scanState.result.items.count { it.selected }
                    Button(
                        onClick = onCommitScan,
                        enabled = selectedCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("commit_scan_button")
                    ) {
                        Text("Update Digital Inventory ($selectedCount items)")
                    }
                }
            }

            is ScanUiState.Applying -> {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Syncing inventory database...", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            is ScanUiState.Applied -> {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = "Success",
                                    tint = StatusHealthy,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Updated ${scanState.count} items!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "Your ${snapshot.activeStore?.name ?: "workspace"} stock and activity history are now live.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = onDoneViewingInventory, modifier = Modifier.weight(1f)) {
                                    Text("View Inventory")
                                }
                                OutlinedButton(onClick = onResetScan, modifier = Modifier.weight(1f)) {
                                    Text("Scan Another")
                                }
                            }
                        }
                    }
                }
            }

            is ScanUiState.Error -> {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Scan Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = scanState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(onClick = onResetScan) {
                                Text("Try Again")
                            }
                        }
                    }
                }
            }
        }

        // Recent Scan Activity Feed
        val scanLogs = snapshot.storeLogs.filter { it.type == "scan_in" }.take(6)
        if (scanLogs.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Scan Updates",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(scanLogs, key = { it.id }) { log ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(log.itemName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                log.reason ?: "AI Photo Scan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${if (log.quantityDelta >= 0) "+" else ""}${formatDecimal(log.quantityDelta)} ${log.unit}",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanModeCard(
    type: ScanType,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = modifier
            .border(1.5.dp, borderColor, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("scan_mode_${type.key}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = type.title,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = type.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ScannedCandidateCard(
    candidate: ScannedItemCandidate,
    scanType: ScanType,
    onToggle: () -> Unit,
    onAdjustQty: (Double) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = candidate.selected,
                onCheckedChange = { onToggle() }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = candidate.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val matchBadge = if (candidate.matchedExistingItemId != null) {
                    if (scanType == ScanType.RECEIPT) "Adds to existing" else "Updates existing"
                } else {
                    "New item"
                }
                Text(
                    text = buildString {
                        append(candidate.category)
                        append(" • ")
                        append(matchBadge)
                        if (candidate.estimatedShelfLifeDays > 0) {
                            append(" • ${candidate.estimatedShelfLifeDays}d shelf life")
                        }
                        if (candidate.unitCost > 0.0) {
                            append(String.format(Locale.US, " • $%.2f", candidate.unitCost))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(
                    onClick = { onAdjustQty(-1.0) },
                    enabled = candidate.quantity > 1.0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Decrease quantity", modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "${formatDecimal(candidate.quantity)} ${candidate.unit}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                FilledTonalIconButton(
                    onClick = { onAdjustQty(1.0) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Increase quantity", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun createTempCaptureUri(context: Context): Uri {
    val capturesDir = File(context.cacheDir, "captures").apply { mkdirs() }
    val imageFile = File(capturesDir, "scan_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
}

@Suppress("DEPRECATION")
private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
