package network.columba.app.desktop.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import network.columba.shared.domain.model.Identity
import network.columba.shared.domain.repository.IdentityRepository
import org.koin.java.KoinJavaComponent.getKoin

/**
 * ViewModel for settings screen (Desktop version without Android lifecycle).
 * Manages identities and app settings.
 */
class SettingsViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val identityRepository: IdentityRepository =
        getKoin().get<IdentityRepository>()

    private val _identities = MutableStateFlow<List<Identity>>(emptyList())
    val identities: StateFlow<List<Identity>> = _identities.asStateFlow()

    private val _activeIdentity = MutableStateFlow<Identity?>(null)
    val activeIdentity: StateFlow<Identity?> = _activeIdentity.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        observeIdentities()
    }

    private fun observeIdentities() {
        scope.launch {
            _isLoading.value = true
            try {
                identityRepository.getIdentities().collect { list ->
                    _identities.value = list
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
            }
        }
        scope.launch {
            try {
                identityRepository.getActiveIdentity().collect { active ->
                    _activeIdentity.value = active
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadIdentities() {
        // No-op: kept for backwards compatibility. Reactive flow handles updates.
    }

    fun createIdentity(displayName: String) {
        scope.launch {
            try {
                identityRepository.createIdentity(displayName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectIdentity(identityHash: String) {
        scope.launch {
            try {
                identityRepository.setActiveIdentity(identityHash)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteIdentity(identityHash: String) {
        scope.launch {
            try {
                identityRepository.deleteIdentity(identityHash)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateIdentityName(identityHash: String, newName: String) {
        scope.launch {
            try {
                identityRepository.updateIdentityDisplayName(identityHash, newName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cleanup() {
        (scope.coroutineContext[Job] as? Job)?.cancel()
    }

    /**
     * Export an identity to a JSON file. The format captures everything
     * needed to reconstruct the row on another desktop install (or any
     * Columba client that consumes this format).
     */
    fun exportIdentityToFile(identityHash: String, file: java.io.File, onResult: (Result<Unit>) -> Unit) {
        scope.launch {
            val result = runCatching {
                val export = identityRepository.exportIdentity(identityHash)
                    ?: error("Identity not found")
                val payload = kotlinx.serialization.json.buildJsonObject {
                    put("schema", kotlinx.serialization.json.JsonPrimitive("columba.identity.v1"))
                    put("identityHash", kotlinx.serialization.json.JsonPrimitive(export.identityHash))
                    put("displayName", kotlinx.serialization.json.JsonPrimitive(export.displayName))
                    put("publicKey", kotlinx.serialization.json.JsonPrimitive(java.util.Base64.getEncoder().encodeToString(export.publicKey)))
                    put("privateKey", kotlinx.serialization.json.JsonPrimitive(java.util.Base64.getEncoder().encodeToString(export.privateKey)))
                }
                file.writeText(kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload))
            }
            onResult(result)
        }
    }

    fun importIdentityFromFile(file: java.io.File, onResult: (Result<Identity>) -> Unit) {
        scope.launch {
            val result = runCatching {
                val parsed = kotlinx.serialization.json.Json.parseToJsonElement(file.readText())
                    .let { it as? kotlinx.serialization.json.JsonObject ?: error("Invalid JSON") }
                val schema = parsed["schema"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                require(schema == "columba.identity.v1") { "Unsupported identity file format: $schema" }
                fun str(key: String) = (parsed[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: error("Missing field: $key")
                identityRepository.importIdentity(
                    identityHash = str("identityHash"),
                    displayName = str("displayName"),
                    publicKey = java.util.Base64.getDecoder().decode(str("publicKey")),
                    privateKey = java.util.Base64.getDecoder().decode(str("privateKey")),
                )
            }
            onResult(result)
        }
    }
}
