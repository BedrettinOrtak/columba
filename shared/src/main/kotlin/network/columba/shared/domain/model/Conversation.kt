package network.columba.shared.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Domain model for a conversation.
 * Platform-agnostic representation used across Android and Desktop.
 */
@Serializable
data class Conversation(
    val peerHash: String,
    val peerName: String,
    val displayName: String, // Priority: customNickname > announceName > peerName > peerHash
    val peerPublicKey: String? = null, // Base64 encoded
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int,
    val lastSeenTimestamp: Long = 0,
    // Profile icon data (from announces)
    val iconName: String? = null,
    val iconForegroundColor: String? = null,
    val iconBackgroundColor: String? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonString: String): Conversation = json.decodeFromString(jsonString)
    }

    fun toJson(): String = json.encodeToString(this)
}
