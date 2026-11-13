package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.PlanTier
import com.example.model.UserPersona
import com.example.ui.components.AddStoreDialog
import com.example.ui.theme.PremiumGold
import com.example.ui.theme.StatusHealthy
import com.example.ui.viewmodel.WorkspaceSnapshot

@Composable
fun ProfileScreen(
    snapshot: WorkspaceSnapshot,
    onSwitchPersona: (UserPersona) -> Unit,
    onSwitchTier: (PlanTier) -> Unit,
    onSwitchStore: (String) -> Unit,
    onCreateStore: (String, String, String?) -> Unit,
    onDeleteStore: (String) -> Unit,
    onSignOut: () -> Unit
) {
    var showAddStoreDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Account Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (snapshot.profile?.displayName?.firstOrNull() ?: 'S').uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(
                                text = snapshot.profile?.displayName ?: "StockLens User",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = snapshot.profile?.email ?: "Signed in with Google",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = "Sign out",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // User Profile Mode (User A, User B, User C)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Operational Role", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Tailors dashboard alerts, restock workflows, and Gemini photo prompts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                UserPersona.entries.forEach { persona ->
                    val icon = when (persona) {
                        UserPersona.PANTRY -> Icons.Outlined.Kitchen
                        UserPersona.CHEF -> Icons.Outlined.Restaurant
                        UserPersona.STORE -> Icons.Outlined.Storefront
                    }
                    PersonaOptionCard(
                        persona = persona,
                        icon = icon,
                        selected = snapshot.persona == persona,
                        onClick = { onSwitchPersona(persona) }
                    )
                }
            }
        }

        // Free vs Premium Plan Switcher
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Plan Tier (Free vs Premium)", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlanCard(
                        title = "Free",
                        badge = "Always Usable",
                        features = listOf(
                            "Unlimited AI photo scans",
                            "1 active store/pantry",
                            "Expiry & low-stock alerts",
                            "Waste & restock logs"
                        ),
                        selected = snapshot.tier == PlanTier.FREE,
                        onSelect = { onSwitchTier(PlanTier.FREE) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tier_free_card")
                    )
                    PlanCard(
                        title = "Premium",
                        badge = "Multi-Store + Pro",
                        features = listOf(
                            "Unlimited stores & kitchens",
                            "Cross-store stock health",
                            "Category valuation charts",
                            "Overstock & turnover KPIs"
                        ),
                        selected = snapshot.tier == PlanTier.PREMIUM,
                        onSelect = { onSwitchTier(PlanTier.PREMIUM) },
                        isPremium = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tier_premium_card")
                    )
                }
            }
        }

        // Store & Location Management
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Locations & Stores (${snapshot.stores.size})", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (snapshot.tier == PlanTier.FREE) {
                            "Free plan supports 1 location"
                        } else {
                            "Premium multi-store workspace active"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (snapshot.tier == PlanTier.PREMIUM) {
                    Button(
                        onClick = { showAddStoreDialog = true },
                        modifier = Modifier.testTag("add_store_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Outlined.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Store")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSwitchTier(PlanTier.PREMIUM) },
                        modifier = Modifier.testTag("unlock_multi_store_button")
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = PremiumGold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unlock More")
                    }
                }
            }
        }

        items(snapshot.stores, key = { it.id }) { store ->
            val isActive = snapshot.activeStore?.id == store.id
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onSwitchStore(store.id) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = store.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${store.type.replaceFirstChar { it.uppercase() }}" +
                                if (!store.address.isNullOrBlank()) " • ${store.address}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isActive) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Active location",
                                tint = StatusHealthy,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (!store.isDefault) {
                            IconButton(onClick = { onDeleteStore(store.id) }) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete ${store.name}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showAddStoreDialog) {
        AddStoreDialog(
            onDismiss = { showAddStoreDialog = false },
            onCreate = { name, type, address ->
                onCreateStore(name, type, address)
                showAddStoreDialog = false
            }
        )
    }
}

@Composable
private fun PersonaOptionCard(
    persona: UserPersona,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.medium
            )
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("persona_${persona.key}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = persona.title,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(persona.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    persona.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    badge: String,
    features: List<String>,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    isPremium: Boolean = false
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    if (isPremium) PremiumGold else MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                },
                shape = MaterialTheme.shapes.medium
            )
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onSelect)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isPremium) {
                    Icon(
                        Icons.Outlined.WorkspacePremium,
                        contentDescription = null,
                        tint = PremiumGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = (if (isPremium) PremiumGold else MaterialTheme.colorScheme.primary).copy(alpha = 0.14f)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPremium) PremiumGold else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                features.forEach { feat ->
                    Text(
                        text = "• $feat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = if (selected) "Active Profile" else "Tap to Select",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
