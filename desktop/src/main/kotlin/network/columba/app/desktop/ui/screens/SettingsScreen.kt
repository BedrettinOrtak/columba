package network.columba.app.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.java.KoinJavaComponent.getKoin
import network.columba.app.desktop.AppState
import network.columba.app.desktop.ui.viewmodel.SettingsViewModel
import network.columba.app.desktop.i18n.Language
import network.columba.app.desktop.i18n.Strings

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, appState: AppState) {
    val viewModel: SettingsViewModel = remember { SettingsViewModel() }
    val identities by viewModel.identities.collectAsState()
    val activeIdentity by viewModel.activeIdentity.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val strings by Strings.stringsState.collectAsState()

    // State for new identity dialog
    var showNewIdentityDialog by remember { mutableStateOf(false) }
    var newIdentityName by remember { mutableStateOf("") }

    // State for edit identity dialog
    var showEditDialog by remember { mutableStateOf(false) }
    var editingIdentity by remember { mutableStateOf<network.columba.shared.domain.model.Identity?>(null) }
    var editName by remember { mutableStateOf("") }

    // Get AppState reference for LanguageSelector
    val appStateReference = appState

    // Current language
    val currentLanguage by appState.currentLanguage

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = strings.settingsTab,
            style = MaterialTheme.typography.headlineLarge
        )

        // Language Settings
        SettingsSection(title = strings.settingsTab) {
            LanguageSelector(strings = strings, appState = appStateReference)
        }

        // Identity Settings
        SettingsSection(title = strings.yourIdentities) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.yourIdentities,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = Strings.formatCount(strings.identitiesConfigured, identities.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = { showNewIdentityDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.new)
                }
            }

            // Import/Export controls. Uses Swing JFileChooser since we're on
            // a Compose Desktop window — no separate filepicker dependency.
            var ioFeedback by remember { mutableStateOf<String?>(null) }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val chooser = javax.swing.JFileChooser().apply {
                            dialogTitle = "Import identity (.json)"
                            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Columba identity (*.json)", "json")
                        }
                        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                            viewModel.importIdentityFromFile(chooser.selectedFile) { result ->
                                ioFeedback = result.fold(
                                    onSuccess = { "Imported ${it.displayName}" },
                                    onFailure = { "Import failed: ${it.message}" },
                                )
                            }
                        }
                    },
                ) { Text("Import") }
                OutlinedButton(
                    enabled = activeIdentity != null,
                    onClick = {
                        val active = activeIdentity ?: return@OutlinedButton
                        val chooser = javax.swing.JFileChooser().apply {
                            dialogTitle = "Export identity (.json)"
                            selectedFile = java.io.File("${active.displayName.replace(' ', '_')}.identity.json")
                        }
                        if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                            viewModel.exportIdentityToFile(active.identityHash, chooser.selectedFile) { result ->
                                ioFeedback = result.fold(
                                    onSuccess = { "Exported to ${chooser.selectedFile.name}" },
                                    onFailure = { "Export failed: ${it.message}" },
                                )
                            }
                        }
                    },
                ) { Text("Export active") }
            }
            ioFeedback?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (identities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                identities.forEach { identity ->
                    IdentityItem(
                        identity = identity,
                        isActive = activeIdentity?.identityHash == identity.identityHash,
                        onSelect = { viewModel.selectIdentity(identity.identityHash) },
                        onEdit = {
                            editingIdentity = identity
                            editName = identity.displayName
                            showEditDialog = true
                        },
                        onDelete = { viewModel.deleteIdentity(identity.identityHash) },
                        strings = strings,
                        appState = appStateReference
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else if (!isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠ No Identity Configured",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Create an identity to start messaging on the Reticulum network",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(onClick = { showNewIdentityDialog = true }) {
                            Text(strings.createIdentity)
                        }
                    }
                }
            }
        }

        // Network Settings
        SettingsSection(title = strings.settingsTab) {
            var reticulumEnabled by remember { mutableStateOf(true) }

            SettingItem(
                title = strings.reticulumService,
                description = strings.reticulumEnabled,
                control = {
                    Switch(
                        checked = reticulumEnabled,
                        onCheckedChange = { reticulumEnabled = it }
                    )
                }
            )
            SettingItem(
                title = "Auto Announce",
                description = strings.autoAnnounce,
                control = {
                    Switch(
                        checked = true,
                        onCheckedChange = { }
                    )
                }
            )
        }

        // Interface Settings
        SettingsSection(title = strings.settingsTab) {
            val rnsService = remember {
                runCatching {
                    getKoin().get<network.columba.desktop.data.reticulum.DesktopReticulumService>()
                }.getOrNull()
            }
            var tcpHost by remember { mutableStateOf("") }
            var tcpPort by remember { mutableStateOf("4242") }
            var tcpFeedback by remember { mutableStateOf<String?>(null) }

            SettingItem(
                title = strings.tcpInterface,
                description = strings.connectViaTcp,
                control = {}
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = tcpHost,
                    onValueChange = { tcpHost = it },
                    label = { Text("Host") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = tcpPort,
                    onValueChange = { tcpPort = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = rnsService != null && tcpHost.isNotBlank() && tcpPort.toIntOrNull() != null,
                    onClick = {
                        val port = tcpPort.toIntOrNull() ?: return@Button
                        tcpFeedback = runCatching {
                            rnsService?.addTcpClientInterface(tcpHost.trim(), port)
                        }.fold(
                            onSuccess = { "Added $tcpHost:$port" },
                            onFailure = { "Failed: ${it.message}" },
                        )
                    },
                ) { Text("Add") }
            }
            tcpFeedback?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // UDP broadcast interface — universal IPv4 LAN fallback.
            // Default port 4242 matches the Python RNS reference config.
            Spacer(modifier = Modifier.height(16.dp))
            var udpForward by remember { mutableStateOf("255.255.255.255") }
            var udpPort by remember { mutableStateOf("4242") }
            var udpFeedback by remember { mutableStateOf<String?>(null) }
            SettingItem(
                title = "UDP Broadcast",
                description = "Local LAN mesh (IPv4) — default 255.255.255.255:4242",
                control = {}
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = udpForward,
                    onValueChange = { udpForward = it },
                    label = { Text("Forward IP") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = udpPort,
                    onValueChange = { udpPort = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = rnsService != null && udpForward.isNotBlank() && udpPort.toIntOrNull() != null,
                    onClick = {
                        val port = udpPort.toIntOrNull() ?: return@Button
                        udpFeedback = runCatching {
                            rnsService?.addUdpInterface(
                                bindIp = null,
                                bindPort = port,
                                forwardIp = udpForward.trim(),
                                forwardPort = port,
                                broadcast = true,
                            )
                        }.fold(
                            onSuccess = { "Added UDP $udpForward:$port" },
                            onFailure = { "Failed: ${it.message}" },
                        )
                    },
                ) { Text("Add") }
            }
            udpFeedback?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // TCP server interface — host a Reticulum TCP listener that
            // remote peers can connect to. Useful when this machine has a
            // routable address or port-forwarding from the LAN gateway.
            Spacer(modifier = Modifier.height(16.dp))
            var tcpsBind by remember { mutableStateOf("0.0.0.0") }
            var tcpsPort by remember { mutableStateOf("4242") }
            var tcpsFeedback by remember { mutableStateOf<String?>(null) }
            SettingItem(
                title = "TCP Server",
                description = "Listen for incoming peer connections",
                control = {}
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = tcpsBind,
                    onValueChange = { tcpsBind = it },
                    label = { Text("Bind") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = tcpsPort,
                    onValueChange = { tcpsPort = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = rnsService != null && tcpsBind.isNotBlank() && tcpsPort.toIntOrNull() != null,
                    onClick = {
                        val port = tcpsPort.toIntOrNull() ?: return@Button
                        tcpsFeedback = runCatching {
                            rnsService?.addTcpServerInterface(tcpsBind.trim(), port)
                        }.fold(
                            onSuccess = { "Listening on $tcpsBind:$port" },
                            onFailure = { "Failed: ${it.message}" },
                        )
                    },
                ) { Text("Listen") }
            }
            tcpsFeedback?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SettingItem(
                title = strings.i2pInterface,
                description = strings.connectViaI2p,
                control = {
                    Switch(
                        checked = false,
                        onCheckedChange = { }
                    )
                }
            )
        }

        // About
        SettingsSection(title = strings.about) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Columba Desktop",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${strings.version}: 0.7.3-dev",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = strings.appDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // New Identity Dialog
    if (showNewIdentityDialog) {
        AlertDialog(
            onDismissRequest = { showNewIdentityDialog = false },
            title = { Text(strings.createIdentity) },
            text = {
                OutlinedTextField(
                    value = newIdentityName,
                    onValueChange = { newIdentityName = it },
                    label = { Text(strings.displayName) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newIdentityName.isNotBlank()) {
                            viewModel.createIdentity(newIdentityName)
                            newIdentityName = ""
                            showNewIdentityDialog = false
                        }
                    },
                    enabled = newIdentityName.isNotBlank()
                ) {
                    Text(strings.create)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewIdentityDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Edit Identity Dialog
    if (showEditDialog && editingIdentity != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(strings.edit) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(strings.displayName) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editName.isNotBlank()) {
                            viewModel.updateIdentityName(editingIdentity!!.identityHash, editName)
                            showEditDialog = false
                            editingIdentity = null
                        }
                    },
                    enabled = editName.isNotBlank()
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    editingIdentity = null
                }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun LanguageSelector(strings: Strings, appState: AppState) {
    val selectedLanguage by appState.currentLanguage

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Language / Dil / Ziman / اللغة",
            style = MaterialTheme.typography.titleMedium
        )
        Language.getAll().forEach { language ->
            Card(
                onClick = {
                    appState.setLanguage(language)
                    // Save to preferences
                    val preferences = org.koin.java.KoinJavaComponent.getKoin()
                        .get<network.columba.desktop.data.preference.AppPreferences>()
                    preferences.setLanguage(language.code)
                },
                modifier = Modifier
                    .fillMaxWidth(),
                colors = if (selectedLanguage == language) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                } else {
                    CardDefaults.cardColors()
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = language.flag,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Column {
                        Text(
                            text = language.nativeName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = language.code.uppercase(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectedLanguage == language) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityItem(
    identity: network.columba.shared.domain.model.Identity,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    strings: Strings,
    appState: AppState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = identity.displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = identity.identityHash.take(16) + "...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isActive) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    FilledTonalButton(onClick = onSelect) {
                        Text(strings.activate)
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = strings.edit)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = strings.delete)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            HorizontalDivider()
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    description: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        control()
    }
}
