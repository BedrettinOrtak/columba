package network.columba.desktop.data.db.entity

/**
 * Desktop database entity for local identities.
 * Matches Android LocalIdentityEntity structure for compatibility.
 */
data class LocalIdentityEntity(
    val identityHash: String,
    val displayName: String,
    val publicKey: String, // Base64 encoded
    val privateKey: String, // Base64 encoded
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
