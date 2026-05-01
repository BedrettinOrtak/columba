package network.columba.shared.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Domain model for a message in a conversation.
 * Platform-agnostic representation used across Android and Desktop.
 */
@Serializable
data class Message(
    val id: String,
    val destinationHash: String,
    val content: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val status: String = "sent", // "pending", "sent", "delivered", "failed"
    val isRead: Boolean = false,
    val fieldsJson: String? = null, // LXMF fields (attachments, images, audio, etc.)
    val deliveryMethod: String? = null, // "opportunistic", "direct", "propagated"
    val errorMessage: String? = null,
    val replyToMessageId: String? = null,
    val receivedHopCount: Int? = null,
    val receivedInterface: String? = null,
    val receivedRssi: Int? = null, // dBm
    val receivedSnr: Float? = null, // dB
    val receivedAt: Long? = null, // Local reception timestamp
    val sentInterface: String? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonString: String): Message = json.decodeFromString(jsonString)
    }

    fun toJson(): String = json.encodeToString(this)
}
