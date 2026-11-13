package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.ai.InventoryScannerService
import com.example.data.repository.InventoryRepository
import com.example.model.PlanTier
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.StockLensViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_INVENTORY = "inventory"
const val ROUTE_SCAN = "scan"
const val ROUTE_INSIGHTS = "insights"
const val ROUTE_PROFILE = "profile"

private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val NAV_ITEMS = listOf(
    NavItem(ROUTE_INVENTORY, "Stock", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    NavItem(ROUTE_SCAN, "Scan", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt),
    NavItem(ROUTE_INSIGHTS, "Insights", Icons.Filled.Insights, Icons.Outlined.Insights),
    NavItem(ROUTE_PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedWorkspace(
                currentUserId = user.uid,
                displayName = user.displayName ?: "StockLens User",
                email = user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedWorkspace(
    currentUserId: String,
    displayName: String,
    email: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: StockLensViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StockLensViewModel(
                    repository = InventoryRepository(db),
                    scannerService = InventoryScannerService(),
                    currentUserId = currentUserId,
                    initialDisplayName = displayName,
                    initialEmail = email
                )
            }
        }
    )

    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val selectedScanType by viewModel.selectedScanType.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 840.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("nav_rail_${item.route}")
                            )
                        }
                    }
                }

                when (val state = workspaceState) {
                    is DataUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("Loading StockLens workspace...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    is DataUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Could not load inventory", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = { signOut(credentialManager, scope) }) {
                                    Text("Sign Out")
                                }
                            }
                        }
                    }

                    is DataUiState.Success -> {
                        val snapshot = state.data
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_INVENTORY,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(ROUTE_INVENTORY) {
                                InventoryScreen(
                                    snapshot = snapshot,
                                    searchQuery = searchQuery,
                                    selectedFilter = selectedFilter,
                                    selectedCategory = selectedCategory,
                                    onSearchChange = viewModel::setSearchQuery,
                                    onFilterChange = viewModel::setSelectedFilter,
                                    onCategoryChange = viewModel::setSelectedCategory,
                                    onSwitchStore = viewModel::switchActiveStore,
                                    onNavigateToScan = { navigateTo(ROUTE_SCAN) },
                                    onNavigateToInsights = { navigateTo(ROUTE_INSIGHTS) },
                                    onNavigateToProfile = { navigateTo(ROUTE_PROFILE) },
                                    onSaveItem = viewModel::saveItem,
                                    onAdjustStock = { item, delta ->
                                        viewModel.adjustStock(
                                            item = item,
                                            delta = delta,
                                            logType = if (delta < 0) "consumed" else "adjustment",
                                            reason = if (delta < 0) "Quick usage" else "Quick stock add"
                                        )
                                    },
                                    onLogWaste = viewModel::logWaste,
                                    onRequestRestock = viewModel::requestRestock,
                                    onDeleteItem = viewModel::deleteItem
                                )
                            }

                            composable(ROUTE_SCAN) {
                                ScanScreen(
                                    snapshot = snapshot,
                                    selectedScanType = selectedScanType,
                                    scanState = scanState,
                                    onSelectScanType = viewModel::setSelectedScanType,
                                    onAnalyzeBitmap = viewModel::analyzeCapturedImage,
                                    onToggleCandidate = viewModel::toggleCandidateSelected,
                                    onUpdateCandidateQty = viewModel::updateCandidateQuantity,
                                    onCommitScan = viewModel::commitScannedItems,
                                    onResetScan = viewModel::resetScanState,
                                    onDoneViewingInventory = {
                                        viewModel.resetScanState()
                                        navigateTo(ROUTE_INVENTORY)
                                    }
                                )
                            }

                            composable(ROUTE_INSIGHTS) {
                                InsightsScreen(
                                    snapshot = snapshot,
                                    onAutoGenerateRestock = viewModel::autoGenerateRestockOrders,
                                    onRequestSingleRestock = { item ->
                                        val qty = ((item.minThreshold * 2.0) - item.quantity).coerceAtLeast(2.0)
                                        viewModel.requestRestock(item, qty, "Low stock replenishment")
                                    },
                                    onAdvanceOrderStatus = viewModel::advanceOrderStatus,
                                    onUpgradeToPremium = { viewModel.switchPlanTier(PlanTier.PREMIUM) }
                                )
                            }

                            composable(ROUTE_PROFILE) {
                                ProfileScreen(
                                    snapshot = snapshot,
                                    onSwitchPersona = viewModel::switchPersona,
                                    onSwitchTier = viewModel::switchPlanTier,
                                    onSwitchStore = viewModel::switchActiveStore,
                                    onCreateStore = viewModel::createStore,
                                    onDeleteStore = viewModel::deleteStore,
                                    onSignOut = { signOut(credentialManager, scope) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
