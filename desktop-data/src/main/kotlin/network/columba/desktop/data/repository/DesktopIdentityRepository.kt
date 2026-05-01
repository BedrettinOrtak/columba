package network.columba.desktop.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import network.columba.desktop.data.db.ColumbaDatabase
import network.columba.desktop.data.db.dao.LocalIdentityDao
import network.columba.desktop.data.db.entity.LocalIdentityEntity
import network.columba.desktop.data.reticulum.DesktopReticulumService
import network.columba.shared.domain.model.Identity
import network.columba.shared.domain.repository.IdentityRepository
import network.reticulum.crypto.BouncyCastleProvider
import network.reticulum.identity.Identity as RnsIdentity
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Desktop implementation of IdentityRepository.
 * Uses SQLite JDBC for data persistence and reticulum-kt for the actual
 * Reticulum identity primitives — keys generated here are interoperable
 * with the Android client and any reference Python RNS peer.
 */
class DesktopIdentityRepository(
    private val database: ColumbaDatabase,
    private val reticulumService: DesktopReticulumService? = null,
) : IdentityRepository {
    private val logger = LoggerFactory.getLogger(DesktopIdentityRepository::class.java)
    private val localIdentityDao = LocalIdentityDao { database.getConnection() }

    /** Bumped on every write so observers re-query the DAO. */
    private val refreshTrigger = MutableStateFlow(0L)

    private fun notifyChanged() {
        refreshTrigger.value = System.nanoTime()
    }

    override fun getIdentities(): Flow<List<Identity>> {
        return refreshTrigger
            .map { localIdentityDao.getAllIdentities().map { it.toIdentity() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getActiveIdentity(): Flow<Identity?> {
        return refreshTrigger
            .map { localIdentityDao.getActiveIdentity()?.toIdentity() }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun setActiveIdentity(identityHash: String) {
        localIdentityDao.setActiveIdentity(identityHash)
        notifyChanged()
    }

    override suspend fun createIdentity(displayName: String): Identity {
        // Generate a real Reticulum identity. The Identity class wraps an
        // Ed25519 signing key + X25519 exchange key in the exact byte layout
        // expected on the wire by both Python RNS and the Android client.
        // Using anything else (e.g. JCA Ed25519) would produce hashes that
        // wouldn't resolve on the Reticulum mesh.
        val rnsIdentity = RnsIdentity.Companion.create(reticulumService?.cryptoProvider ?: BouncyCastleProvider())
        val identityHash = rnsIdentity.hexHash

        val identity = LocalIdentityEntity(
            identityHash = identityHash,
            displayName = displayName,
            publicKey = encodeBytes(rnsIdentity.getPublicKey()),
            privateKey = encodeBytes(rnsIdentity.getPrivateKey()),
            isActive = true,
            createdAt = System.currentTimeMillis(),
        )

        // Use transaction to deactivate all and insert new
        database.withConnection { _ ->
            localIdentityDao.setActiveIdentity(identityHash)
            localIdentityDao.insertIdentity(identity)
        }

        notifyChanged()
        return identity.toIdentity()
    }

    override suspend fun deleteIdentity(identityHash: String) {
        localIdentityDao.deleteIdentity(identityHash)
        notifyChanged()
    }

    override suspend fun updateIdentityDisplayName(identityHash: String, displayName: String) {
        val existing = localIdentityDao.getIdentity(identityHash)
        if (existing != null) {
            val updated = existing.copy(displayName = displayName)
            localIdentityDao.updateIdentity(updated)
            notifyChanged()
        }
    }

    override suspend fun importIdentity(
        identityHash: String,
        displayName: String,
        publicKey: ByteArray,
        privateKey: ByteArray,
    ): Identity {
        val identity = LocalIdentityEntity(
            identityHash = identityHash,
            displayName = displayName,
            publicKey = encodeBytes(publicKey),
            privateKey = encodeBytes(privateKey),
            isActive = false,
        )

        localIdentityDao.insertIdentity(identity)
        notifyChanged()
        return identity.toIdentity()
    }

    override suspend fun exportIdentity(identityHash: String): IdentityRepository.IdentityExport? {
        val identity = localIdentityDao.getIdentity(identityHash)
        return identity?.let {
            IdentityRepository.IdentityExport(
                identityHash = it.identityHash,
                displayName = it.displayName,
                publicKey = decodeBytes(it.publicKey),
                privateKey = decodeBytes(it.privateKey),
            )
        }
    }

    /**
     * Get all identities synchronously (for testing/debugging).
     */
    suspend fun getIdentitiesSync(): List<Identity> {
        return localIdentityDao.getAllIdentities().map { it.toIdentity() }
    }

    /**
     * Get active identity synchronously (for testing/debugging).
     */
    suspend fun getActiveIdentitySync(): Identity? {
        val identity = localIdentityDao.getActiveIdentity()
        return identity?.toIdentity()
    }

    private fun LocalIdentityEntity.toIdentity(): Identity {
        return Identity(
            identityHash = identityHash,
            displayName = displayName,
            publicKey = publicKey,
            privateKey = privateKey,
            isActive = isActive,
            createdAt = createdAt,
        )
    }

    private fun encodeBytes(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun decodeBytes(base64: String): ByteArray {
        return Base64.getDecoder().decode(base64)
    }
}
