package network.columba.desktop.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import network.columba.desktop.data.db.ColumbaDatabase
import network.columba.desktop.data.db.dao.LocalIdentityDao
import network.columba.desktop.data.db.entity.LocalIdentityEntity
import network.columba.shared.domain.model.Identity
import network.columba.shared.domain.repository.IdentityRepository
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Desktop implementation of IdentityRepository.
 * Uses SQLite JDBC for data persistence.
 */
class DesktopIdentityRepository(
    private val database: ColumbaDatabase,
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
        // Generate identity keys using Java security APIs
        // Note: In production, use proper Reticulum identity generation
        val keyPair = generateKeyPair()
        val identityHash = generateIdentityHash(keyPair.public)

        val identity = LocalIdentityEntity(
            identityHash = identityHash,
            displayName = displayName,
            publicKey = encodeBytes(keyPair.public.encoded),
            privateKey = encodeBytes(keyPair.private.encoded),
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

    /**
     * Generate a simple key pair for demonstration.
     * In production, use Reticulum's identity generation.
     */
    private fun generateKeyPair(): java.security.KeyPair {
        val keyGen = java.security.KeyPairGenerator.getInstance("Ed25519")
        keyGen.initialize(256)
        return keyGen.generateKeyPair()
    }

    /**
     * Generate a simple identity hash from public key.
     * In production, use Reticulum's identity hash calculation.
     */
    private fun generateIdentityHash(publicKey: java.security.PublicKey): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey.encoded)
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }
}
