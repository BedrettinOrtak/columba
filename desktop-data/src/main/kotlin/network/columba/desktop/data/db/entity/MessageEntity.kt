package network.columba.desktop.data.db.entity

/**
 * Desktop database entity for messages.
 * Matches Android MessageEntity structure for compatibility.
 */
data class MessageEntity(
    val id: String,
    val conversationHash: String,
    val identityHash: String,
    val content: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val status: String = "sent",
    val isRead: Boolean = false,
    val fieldsJson: String? = null,
    val deliveryMethod: String? = null,
    val errorMessage: String? = null,
    val replyToMessageId: String? = null,
    val receivedHopCount: Int? = null,
    val receivedInterface: String? = null,
    val receivedRssi: Int? = null,
    val receivedSnr: Float? = null,
    val receivedAt: Long? = null,
    val sentInterface: String? = null,
)
