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
}
