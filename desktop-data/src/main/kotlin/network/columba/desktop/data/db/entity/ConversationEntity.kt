package network.columba.desktop.data.db.entity

/**
 * Desktop database entity for conversations.
 * Matches Android ConversationEntity structure for compatibility.
 */
data class ConversationEntity(
    val peerHash: String,
    val identityHash: String,
    val peerName: String,
    val peerPublicKey: String? = null, // Base64 encoded
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int = 0,
    val lastSeenTimestamp: Long = 0,
)
