package network.columba.shared.domain.repository

import kotlinx.coroutines.flow.Flow
import network.columba.shared.domain.model.Conversation
import network.columba.shared.domain.model.Message

/**
 * Repository interface for conversation and message operations.
 * Platform-agnostic - implemented separately for Android and Desktop.
 */
interface ConversationRepository {
    /**
     * Get all conversations for the active identity.
     */
    fun getConversations(): Flow<List<Conversation>>

    /**
     * Get all messages for a specific conversation.
     */
    fun getMessages(peerHash: String): Flow<List<Message>>

    /**
     * Save a message and update the conversation.
     * Creates conversation if it doesn't exist.
     */
    suspend fun saveMessage(
        peerHash: String,
        peerName: String,
        message: Message,
        peerPublicKey: String? = null,
    )

    /**
     * Mark a conversation as read.
     */
    suspend fun markConversationAsRead(peerHash: String)

    /**
     * Delete a conversation and all its messages.
     */
    suspend fun deleteConversation(peerHash: String)

    /**
     * Get a specific message by ID.
     */
    suspend fun getMessageById(messageId: String): Message?

    /**
     * Update message status.
     */
    suspend fun updateMessageStatus(messageId: String, status: String)

    /**
     * Get the active identity hash.
     */
    suspend fun getActiveIdentityHash(): String?
}
