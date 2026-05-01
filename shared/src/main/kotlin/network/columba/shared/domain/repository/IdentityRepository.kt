package network.columba.shared.domain.repository

import kotlinx.coroutines.flow.Flow
import network.columba.shared.domain.model.Identity

/**
 * Repository interface for identity operations.
 * Platform-agnostic - implemented separately for Android and Desktop.
 */
interface IdentityRepository {
    /**
     * Get all identities.
     */
    fun getIdentities(): Flow<List<Identity>>

    /**
     * Get the active identity.
     */
    fun getActiveIdentity(): Flow<Identity?>

    /**
     * Set the active identity.
     */
    suspend fun setActiveIdentity(identityHash: String)

    /**
     * Create a new identity.
     */
    suspend fun createIdentity(displayName: String): Identity

    /**
     * Delete an identity.
     */
    suspend fun deleteIdentity(identityHash: String)

    /**
     * Update identity display name.
     */
    suspend fun updateIdentityDisplayName(identityHash: String, displayName: String)

    /**
     * Import an identity from keys.
     */
    suspend fun importIdentity(
        identityHash: String,
        displayName: String,
        publicKey: ByteArray,
        privateKey: ByteArray,
    ): Identity

    /**
     * Export identity as keys.
     */
    suspend fun exportIdentity(identityHash: String): IdentityExport?

    /**
     * Identity export data structure.
     */
    data class IdentityExport(
        val identityHash: String,
        val displayName: String,
        val publicKey: ByteArray,
        val privateKey: ByteArray,
    )
}
