package network.columba.app.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.java.KoinJavaComponent.getKoin
import network.columba.app.desktop.i18n.Strings

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val strings by Strings.stringsState.collectAsState()

    // Drive online status from the live Reticulum service state.
    val rnsService = remember {
        runCatching {
            getKoin().get<network.columba.desktop.data.reticulum.DesktopReticulumService>()
        }.getOrNull()
    }
    val rnsState by (rnsService?.state?.collectAsState()
        ?: remember {
            mutableStateOf(network.columba.desktop.data.reticulum.DesktopReticulumService.State.STOPPED)
        })
    val isOnline = rnsState == network.columba.desktop.data.reticulum.DesktopReticulumService.State.READY

    // Check if there's an active identity
    val identityRepository = getKoin().get<network.columba.shared.domain.repository.IdentityRepository>()
    val hasIdentity by produceState<Boolean?>(null) {
        val repo = identityRepository as? network.columba.desktop.data.repository.DesktopIdentityRepository
        value = repo?.getActiveIdentitySync() != null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Network Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isOnline) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.Wifi else Icons.Outlined.WifiOff,
                    contentDescription = null,
                    tint = if (isOnline) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Column {
                    Text(
                        text = if (isOnline) strings.onlineStatus else strings.offlineStatus,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = if (isOnline) strings.connectedToMesh
                                else strings.noActiveConnections,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOnline) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Identity Status
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = strings.identityStatus,
                    style = MaterialTheme.typography.titleLarge
                )
                if (hasIdentity == true) {
                    Text(
                        text = strings.identityConfigured,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (hasIdentity == false) {
                    Text(
                        text = strings.noIdentityConfigured,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Quick Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = strings.peers,
                value = "0",
                icon = null
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = strings.messages,
                value = "0",
                icon = null
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = strings.interfaces,
                value = "0",
                icon = null
            )
        }

        // Recent Activity
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = strings.recentActivity,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = strings.noRecentActivity,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
