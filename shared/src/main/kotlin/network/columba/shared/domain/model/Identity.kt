package network.columba.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model for a local identity (user's own identity).
 * Platform-agnostic representation used across Android and Desktop.
 */
@Serializable
data class Identity(
    val identityHash: String,
    val displayName: String,
    val publicKey: String, // Base64 encoded
    val privateKey: String, // Base64 encoded
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /**
     * Convert public key from ByteArray to Base64 String.
     */
    companion object {
        /**
         * Convert Base64 String back to ByteArray.
         * Desktop-compatible version using Java Base64.
         */
        fun decodePublicKey(base64: String): ByteArray {
            return java.util.Base64.getDecoder().decode(base64)
        }

        fun decodePrivateKey(base64: String): ByteArray {
            return java.util.Base64.getDecoder().decode(base64)
        }

        /**
         * Encode ByteArray to Base64 String (desktop-compatible).
         */
        fun encodePublicKey(bytes: ByteArray): String {
            return java.util.Base64.getEncoder().encodeToString(bytes)
        }

        fun encodePrivateKey(bytes: ByteArray): String {
            return java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }
}
