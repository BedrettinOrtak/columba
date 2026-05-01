package network.columba.app.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import network.columba.app.R
import network.columba.app.data.model.SignalQuality
import network.columba.app.ui.components.BluetoothPermissionController
import network.columba.app.ui.components.QrCodeImage
import network.columba.app.ui.components.ServiceRestartBanner
import network.columba.app.ui.components.rememberBluetoothPermissionController
import network.columba.app.ui.util.rememberLifecycleTickerMillis
import network.columba.app.util.IdentityQrCodeUtils
import network.columba.app.viewmodel.BleConnectionsUiState
import network.columba.app.viewmodel.DebugInfo
import network.columba.app.viewmodel.DebugViewModel
import network.columba.app.viewmodel.InterfaceInfo
import network.columba.app.viewmodel.TestAnnounceResult
import kotlinx.coroutines.launch

/**
 * Network Status Screen
 * Shows network monitoring information: BLE connections, interfaces, Reticulum status, test tools.
 * Identity features moved to MyIdentityScreen for clear separation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityScreen(
    onBackClick: () -> Unit = {},
    settingsViewModel: network.columba.app.viewmodel.SettingsViewModel,
    viewModel: DebugViewModel = hiltViewModel(),
    bleConnectionsViewModel: network.columba.app.viewmodel.BleConnectionsViewModel = hiltViewModel(),
    onNavigateToBleStatus: () -> Unit = {},
    onNavigateToInterfaceStats: (Long) -> Unit = {},
    onNavigateToInterfaceManagement: () -> Unit = {},
) {
    val context = LocalContext.current
    val debugInfo by viewModel.debugInfo.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val testResult by viewModel.testAnnounceResult.collectAsState()
    val bleConnectionsState by bleConnectionsViewModel.uiState.collectAsState()
    val isRestarting by viewModel.isRestarting.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()

    // Bluetooth enable launcher
    val bluetoothEnableLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            // Bluetooth state will be updated automatically via flow
            Log.d("IdentityScreen", "Bluetooth enable result: ${result.resultCode}")
        }

    val btController: BluetoothPermissionController =
        rememberBluetoothPermissionController(
            onEnableRequested = { _ ->
                bleConnectionsViewModel.getEnableBluetoothIntent()?.let { intent ->
                    bluetoothEnableLauncher.launch(intent)
                }
            },
            onOpenSettingsRequested = { ctx ->
                val intent = bleConnectionsViewModel.getBluetoothSettingsIntent()
                ctx.startActivity(intent)
            },
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_network_status)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // BLE Connections Card
            BleConnectionsCard(
                uiState = bleConnectionsState,
                onViewDetails = onNavigateToBleStatus,
                onEnableBluetooth = btController.onEnableClick,
                onOpenBluetoothSettings = btController.onOpenSettingsClick,
                isSharedInstance = settingsState.isSharedInstance,
                sharedInstanceOnline = settingsState.sharedInstanceOnline,
            )

            // Status Card
            StatusCard(
                isLoading = debugInfo.isLoading,
                initialized = debugInfo.initialized,
                networkStatus = networkStatus,
                error = debugInfo.error,
            )

            // Service Control Card
            ServiceControlCard(
                onShutdown = { viewModel.shutdownService() },
                onRestart = { viewModel.restartService() },
                isSharedInstance = settingsState.isSharedInstance,
                sharedInstanceOnline = settingsState.sharedInstanceOnline,
            )

            if (isRestarting) {
                ServiceRestartBanner()
            }

            // Interfaces Card
            InterfacesCard(
                interfaces = debugInfo.interfaces,
                viewModel = viewModel,
                onNavigateToInterfaceStats = onNavigateToInterfaceStats,
                onNavigateToInterfaceManagement = onNavigateToInterfaceManagement,
            )

            // Test Actions Card
            TestActionsCard(
                onTestAnnounce = { viewModel.createTestAnnounce() },
                testResult = testResult,
                onClearResult = { viewModel.clearTestResult() },
            )

            // Reticulum Info Card (auto-refreshes every second for live heartbeat)
            ReticulumInfoCard(
                debugInfo = debugInfo,
                onRefresh = { viewModel.refreshDebugInfo() },
            )

            // Bottom spacing for navigation bar (fixed height since M3 NavigationBar consumes the insets)
            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // (restart banner is shown inline in the Column above)
}

@Composable
fun StatusCard(
    isLoading: Boolean = false,
    initialized: Boolean,
    networkStatus: String,
    error: String?,
) {
    val isConnecting = networkStatus == "CONNECTING"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        isLoading || isConnecting -> MaterialTheme.colorScheme.tertiaryContainer
                        initialized && error == null -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector =
                        when {
                            isLoading || isConnecting -> Icons.Default.Refresh
                            initialized && error == null -> Icons.Default.CheckCircle
                            else -> Icons.Default.Warning
                        },
                    contentDescription = null,
                    tint =
                        when {
                            isLoading || isConnecting -> MaterialTheme.colorScheme.onTertiaryContainer
                            initialized && error == null -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        },
                )
                Text(
                    text = stringResource(R.string.identity_reticulum_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Divider()

            InfoRow(
                label = stringResource(R.string.identity_initialized),
                value =
                    if (isLoading) {
                        "Loading..."
                    } else if (initialized) {
                        "Yes"
                    } else {
                        "No"
                    },
            )
            InfoRow(label = stringResource(R.string.main_network_status), value = if (isLoading) stringResource(R.string.identity_loading) else networkStatus)

            if (isLoading || isConnecting) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = if (isLoading) stringResource(R.string.identity_fetching_service_status) else stringResource(R.string.identity_reconnecting_to_service),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            if (error != null) {
                Text(
                    text = "Error: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp),
                            ).padding(8.dp),
                )
            }
        }
    }
}

@Composable
fun ReticulumInfoCard(
    debugInfo: DebugInfo,
    onRefresh: (() -> Unit)? = null,
) {
    if (onRefresh != null) {
        val refreshTick = rememberLifecycleTickerMillis(periodMs = 1_000L)
        androidx.compose.runtime.LaunchedEffect(refreshTick) {
            onRefresh()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.identity_reticulum_information),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Divider()

            InfoRow(label = stringResource(R.string.identity_rns_available), value = if (debugInfo.reticulumAvailable) "Yes" else "No")
            InfoRow(label = stringResource(R.string.identity_storage_path), value = debugInfo.storagePath, monospace = true)
            InfoRow(label = stringResource(R.string.identity_transport_enabled), value = if (debugInfo.transportEnabled) "Yes" else "No")
            InfoRow(label = stringResource(R.string.identity_multicast_lock), value = if (debugInfo.multicastLockHeld) stringResource(R.string.identity_held) else stringResource(R.string.identity_not_held))
            InfoRow(label = stringResource(R.string.identity_wake_lock), value = if (debugInfo.wakeLockHeld) stringResource(R.string.identity_held_4f13) else stringResource(R.string.identity_not_held_94d2))

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = stringResource(R.string.identity_process_persistence),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )

            InfoRow(
                label = stringResource(R.string.identity_heartbeat),
                value =
                    if (debugInfo.heartbeatAgeSeconds >= 0) {
                        "${debugInfo.heartbeatAgeSeconds}s ago"
                    } else {
                        "Not started"
                    },
            )
            InfoRow(
                label = stringResource(R.string.identity_health_check),
                value = if (debugInfo.healthCheckRunning) "✓ Running" else "✗ Stopped",
            )
            InfoRow(
                label = stringResource(R.string.identity_network_monitor),
                value = if (debugInfo.networkMonitorRunning) "✓ Running" else "✗ Stopped",
            )
            InfoRow(
                label = stringResource(R.string.identity_lock_maintenance),
                value = if (debugInfo.maintenanceRunning) "✓ Running" else "✗ Stopped",
            )
            InfoRow(
                label = stringResource(R.string.identity_last_lock_refresh),
                value =
                    if (debugInfo.lastLockRefreshAgeSeconds >= 0) {
                        "${debugInfo.lastLockRefreshAgeSeconds}s ago"
                    } else {
                        "Not yet"
                    },
            )
            if (debugInfo.failedInterfaceCount > 0) {
                InfoRow(
                    label = stringResource(R.string.identity_failed_interfaces),
                    value = "${debugInfo.failedInterfaceCount} (auto-retrying)",
                )
            }
        }
    }
}

@Composable
fun InterfacesCard(
    interfaces: List<InterfaceInfo>,
    viewModel: DebugViewModel? = null,
    onNavigateToInterfaceStats: (Long) -> Unit = {},
    onNavigateToInterfaceManagement: () -> Unit = {},
) {
    var selectedInterface by remember { mutableStateOf<InterfaceInfo?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Network Interfaces (${interfaces.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onNavigateToInterfaceManagement,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.identity_manage_interfaces),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Divider()

            if (interfaces.isEmpty()) {
                Text(
                    text = stringResource(R.string.identity_no_interfaces_configured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                interfaces.forEach { iface ->
                    // RNode interfaces are clickable to navigate to stats screen
                    val isRNode = iface.type.contains("RNode", ignoreCase = true)
                    InterfaceRow(
                        iface = iface,
                        onClick =
                            when {
                                // RNode interfaces navigate to stats screen
                                isRNode && viewModel != null -> {
                                    {
                                        coroutineScope.launch {
                                            val interfaceId = viewModel.findInterfaceIdByName(iface.name)
                                            if (interfaceId != null) {
                                                onNavigateToInterfaceStats(interfaceId)
                                            }
                                        }
                                    }
                                }
                                // Offline/failed interfaces show error dialog
                                !iface.online || iface.error != null -> {
                                    { selectedInterface = iface }
                                }
                                else -> null
                            },
                        showChevron = isRNode,
                    )
                }
            }
        }
    }

    // Error dialog for offline/failed interfaces
    selectedInterface?.let { iface ->
        val hasFailed = iface.error != null

        AlertDialog(
            onDismissRequest = { selectedInterface = null },
            icon = {
                Icon(
                    if (hasFailed) Icons.Default.Error else Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(if (hasFailed) stringResource(R.string.identity_interface_failed) else stringResource(R.string.identity_interface_offline)) },
            text = {
                Column {
                    Text(
                        text = iface.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (hasFailed) {
                        Text(
                            text = stringResource(R.string.identity_this_interface_failed_to_start),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = iface.error ?: stringResource(R.string.identity_unknown_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text =
                                "Another Reticulum app may be using this interface. " +
                                    "Close other apps or disable this interface in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.identity_this_interface_is_currently_offline_and),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.identity_check_that_the_device_is_powered),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedInterface = null }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
fun InterfaceRow(
    iface: InterfaceInfo,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
) {
    val hasFailed = iface.error != null

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                ).then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = iface.type,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = iface.name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    when {
                        iface.online -> Icons.Default.CheckCircle
                        hasFailed -> Icons.Default.Error
                        else -> Icons.Default.Warning
                    },
                contentDescription =
                    when {
                        iface.online -> "Online"
                        hasFailed -> "Failed to start - tap for details"
                        else -> "Offline - tap for details"
                    },
                tint =
                    when {
                        iface.online -> MaterialTheme.colorScheme.primary
                        hasFailed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.tertiary
                    },
            )
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.identity_view_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun TestActionsCard(
    onTestAnnounce: () -> Unit,
    testResult: TestAnnounceResult?,
    onClearResult: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.identity_test_actions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Divider()

            Button(
                onClick = onTestAnnounce,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.identity_send_test_announce))
            }

            if (testResult != null) {
                if (testResult.success) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "Test announce sent!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (testResult.hexHash != null) {
                                Text(
                                    "Hash: ${testResult.hexHash}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            TextButton(onClick = onClearResult) {
                                Text(stringResource(R.string.identity_dismiss))
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    "Error sending announce",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (testResult.error != null) {
                                Text(
                                    testResult.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            TextButton(onClick = onClearResult) {
                                Text(stringResource(R.string.identity_dismiss_c8a5))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserIdentityCard(
    displayName: String,
    identityHash: String?,
    destinationHash: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.identity_your_identity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = stringResource(R.string.identity_view_qr_code),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Divider()

            InfoRow(label = stringResource(R.string.welcome_name_label), value = displayName)

            if (destinationHash != null) {
                InfoRow(
                    label = stringResource(R.string.identity_destination),
                    value =
                        IdentityQrCodeUtils.formatHashForDisplay(
                            hash = destinationHash.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                        ),
                    monospace = true,
                )
            }

            Text(
                text = stringResource(R.string.identity_tap_to_view_full_identity_details),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun BleConnectionsCard(
    uiState: BleConnectionsUiState,
    onViewDetails: () -> Unit,
    onEnableBluetooth: () -> Unit = {},
    onOpenBluetoothSettings: () -> Unit = {},
    isSharedInstance: Boolean = false,
    sharedInstanceOnline: Boolean = true,
) {
    // BLE is only disabled when actively connected to shared instance
    // If shared instance went offline, Columba is using its own instance and BLE works
    val bleDisabled = isSharedInstance && sharedInstanceOnline

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.identity_ble_connections),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Divider()

            if (bleDisabled) {
                Text(
                    text =
                        "BLE connections are not available while using a shared Reticulum instance. " +
                            "Only Columba's own instance can initiate Bluetooth LE connections.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                when (uiState) {
                    is BleConnectionsUiState.Loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.identity_loading_connections),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is BleConnectionsUiState.Success -> {
                        if (uiState.totalConnections == 0) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.identity_bluetooth_is_turned_on),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.identity_no_active_ble_connections),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = onOpenBluetoothSettings,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.identity_bluetooth_settings))
                                }
                            }
                        } else {
                            // Summary stats
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp),
                                        ).padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = uiState.totalConnections.toString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = stringResource(R.string.identity_total),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = uiState.centralConnections.toString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = stringResource(R.string.identity_central),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = uiState.peripheralConnections.toString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = stringResource(R.string.identity_peripheral),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // Signal quality indicator
                            val avgSignalQuality =
                                if (uiState.connections.isNotEmpty()) {
                                    val avgRssi =
                                        uiState.connections
                                            .map { it.rssi }
                                            .average()
                                            .toInt()
                                    when {
                                        avgRssi > -50 -> SignalQuality.EXCELLENT
                                        avgRssi > -70 -> SignalQuality.GOOD
                                        avgRssi > -85 -> SignalQuality.FAIR
                                        else -> SignalQuality.POOR
                                    }
                                } else {
                                    SignalQuality.GOOD
                                }

                            val (signalText, signalColor) =
                                when (avgSignalQuality) {
                                    SignalQuality.EXCELLENT -> "Excellent Signal" to MaterialTheme.colorScheme.primary
                                    SignalQuality.GOOD -> "Good Signal" to MaterialTheme.colorScheme.primary
                                    SignalQuality.FAIR -> "Fair Signal" to MaterialTheme.colorScheme.tertiary
                                    SignalQuality.POOR -> "Poor Signal" to MaterialTheme.colorScheme.error
                                }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = signalColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = signalText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = signalColor,
                                    )
                                }
                                TextButton(onClick = onViewDetails) {
                                    Text(stringResource(R.string.identity_view_details_5d5c))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    is BleConnectionsUiState.Error -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Error: ${uiState.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    is BleConnectionsUiState.PermissionsRequired -> {
                        Text(
                            text = stringResource(R.string.identity_bluetooth_permissions_required),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is BleConnectionsUiState.BluetoothDisabled -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BluetoothDisabled,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.identity_bluetooth_is_turned_off),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = onEnableBluetooth,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.identity_turn_on))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen dialog showing complete identity details and QR code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityDetailsDialog(
    displayName: String,
    identityHash: String?,
    destinationHash: String?,
    qrCodeData: String?,
    onDismiss: () -> Unit,
    onShareClick: () -> Unit,
    onNavigateToQrScanner: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.identity_your_identity_a7bc)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.identity_close),
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    )
                },
            ) { paddingValues ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Display Name
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    // QR Code
                    if (qrCodeData != null) {
                        QrCodeImage(
                            data = qrCodeData,
                            size = 280.dp,
                        )

                        Text(
                            text = stringResource(R.string.identity_scan_this_qr_code_to_add),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Action Buttons Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Share Button
                        Button(
                            onClick = onShareClick,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.identity_share))
                        }

                        // Scan QR Button
                        OutlinedButton(
                            onClick = onNavigateToQrScanner,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.identity_scan))
                        }
                    }

                    Divider()

                    // Identity Hash
                    if (identityHash != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.identity_identity_hash),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = identityHash,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(identityHash))
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.identity_copy),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Destination Hash
                    if (destinationHash != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.identity_destination_hash_lxmf),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = destinationHash,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(destinationHash))
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.identity_copy_5fb6),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Service control card with shutdown and restart buttons.
 * Disabled when using a shared instance since Columba doesn't own the service.
 */
@Composable
private fun ServiceControlCard(
    onShutdown: () -> Unit,
    onRestart: () -> Unit,
    isSharedInstance: Boolean = false,
    sharedInstanceOnline: Boolean = true,
) {
    // Service control is only disabled when actively connected to shared instance
    // If shared instance went offline, Columba is using its own instance
    val controlDisabled = isSharedInstance && sharedInstanceOnline

    var showShutdownDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.identity_service_control),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (controlDisabled) {
                Text(
                    text =
                        "Service control is disabled while using a shared Reticulum instance. " +
                            "The network service is managed by another app (e.g., Sideband).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.identity_manually_stop_or_restart_the_background),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showShutdownDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = !controlDisabled,
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.identity_shutdown))
                }

                Button(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f),
                    enabled = !controlDisabled,
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.identity_restart))
                }
            }
        }
    }

    // Confirmation dialog
    if (showShutdownDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showShutdownDialog = false },
            title = { Text(stringResource(R.string.identity_shutdown_service)) },
            text = {
                Text(
                    "This will stop the background Reticulum service. You will not receive messages until you restart the app or manually restart the service.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShutdownDialog = false
                        onShutdown()
                    },
                ) {
                    Text(stringResource(R.string.identity_shutdown_1a4e))
                }
            },
            dismissButton = {
                TextButton(onClick = { showShutdownDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
