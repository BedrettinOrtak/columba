package network.columba.desktop.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import network.columba.desktop.data.db.ColumbaDatabase
import network.columba.desktop.data.db.dao.ConversationDao
import network.columba.desktop.data.db.dao.LocalIdentityDao
import network.columba.desktop.data.db.dao.MessageDao
import network.columba.desktop.data.db.entity.ConversationEntity
import network.columba.desktop.data.db.entity.MessageEntity
import network.columba.shared.domain.model.Conversation
import network.columba.shared.domain.model.Message
import network.columba.shared.domain.repository.ConversationRepository
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Desktop implementation of ConversationRepository.
 * Uses SQLite JDBC for data persistence.
 */
class DesktopConversationRepository(
    private val database: ColumbaDatabase,
) : ConversationRepository {
    private val logger = LoggerFactory.getLogger(DesktopConversationRepository::class.java)
    private val conversationDao = ConversationDao { database.getConnection() }
    private val messageDao = MessageDao { database.getConnection() }
    private val localIdentityDao = LocalIdentityDao { database.getConnection() }

    /** Bumped on every write so observers re-query the DAO. */
    private val refreshTrigger = MutableStateFlow(0L)

    private fun notifyChanged() {
        refreshTrigger.value = System.nanoTime()
    }

    override fun getConversations(): Flow<List<Conversation>> {
        return refreshTrigger
            .map {
                val activeIdentity = localIdentityDao.getActiveIdentity()
                    ?: return@map emptyList()
                conversationDao.getConversations(activeIdentity.identityHash)
                    .map { it.toConversation() }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getMessages(peerHash: String): Flow<List<Message>> {
        return refreshTrigger
            .map {
                val activeIdentity = localIdentityDao.getActiveIdentity()
                    ?: return@map emptyList()
                messageDao.getMessagesForConversation(peerHash, activeIdentity.identityHash)
                    .map { it.toMessage() }
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun saveMessage(
        peerHash: String,
        peerName: String,
        message: Message,
        peerPublicKey: String?,
    ) {
        // Use transaction for atomicity
        database.withConnection { _ ->
            val activeIdentity = localIdentityDao.getActiveIdentity()
                ?: throw IllegalStateException("No active identity found")
            val identityHash = activeIdentity.identityHash

            val messageExists = messageDao.messageExists(message.id, identityHash)

            val existingConversation = conversationDao.getConversation(peerHash, identityHash)
            val conversationTimestamp = message.receivedAt ?: message.timestamp

            if (existingConversation != null) {
                val shouldIncrementUnread = !message.isFromMe && !messageExists
                val updatedConversation = existingConversation.copy(
                    peerName = peerName,
                    peerPublicKey = peerPublicKey ?: existingConversation.peerPublicKey,
                    lastMessage = message.content,
                    lastMessageTimestamp = conversationTimestamp,
                    unreadCount = if (shouldIncrementUnread) {
                        existingConversation.unreadCount + 1
                    } else {
                        existingConversation.unreadCount
                    },
                )
                conversationDao.updateConversation(updatedConversation)
            } else {
                val newConversation = ConversationEntity(
                    peerHash = peerHash,
                    identityHash = identityHash,
                    peerName = peerName,
                    peerPublicKey = peerPublicKey,
                    lastMessage = message.content,
                    lastMessageTimestamp = conversationTimestamp,
                    unreadCount = if (message.isFromMe) 0 else 1,
                )
                conversationDao.insertConversation(newConversation)
            }

            if (!messageExists) {
                val messageEntity = MessageEntity(
                    id = message.id,
                    conversationHash = peerHash,
                    identityHash = identityHash,
                    content = message.content,
                    timestamp = message.timestamp,
                    isFromMe = message.isFromMe,
                    status = message.status,
                    isRead = message.isFromMe,
                    fieldsJson = message.fieldsJson,
                    deliveryMethod = message.deliveryMethod,
                    errorMessage = message.errorMessage,
                    replyToMessageId = message.replyToMessageId,
                    receivedHopCount = message.receivedHopCount,
                    receivedInterface = message.receivedInterface,
                    receivedRssi = message.receivedRssi,
                    receivedSnr = message.receivedSnr,
                    receivedAt = message.receivedAt,
                    sentInterface = message.sentInterface,
                )
                messageDao.insertMessage(messageEntity)
            }
        }
        notifyChanged()
    }

    override suspend fun markConversationAsRead(peerHash: String) {
        val activeIdentity = localIdentityDao.getActiveIdentity() ?: return
        database.withConnection { _ ->
            conversationDao.markAsRead(peerHash, activeIdentity.identityHash)
            messageDao.markMessagesAsRead(peerHash, activeIdentity.identityHash)
        }
        notifyChanged()
    }

    override suspend fun deleteConversation(peerHash: String) {
        val activeIdentity = localIdentityDao.getActiveIdentity() ?: return
        val conversation = conversationDao.getConversation(peerHash, activeIdentity.identityHash)
        conversation?.let {
            conversationDao.deleteConversation(it)
            notifyChanged()
        }
    }

    override suspend fun getMessageById(messageId: String): Message? {
        val activeIdentity = localIdentityDao.getActiveIdentity() ?: return null
        val entity = messageDao.getMessageById(messageId, activeIdentity.identityHash)
        return entity?.toMessage()
    }

    override suspend fun updateMessageStatus(messageId: String, status: String) {
        val activeIdentity = localIdentityDao.getActiveIdentity() ?: return
        messageDao.updateMessageStatus(messageId, activeIdentity.identityHash, status)
        notifyChanged()
    }

    override suspend fun getActiveIdentityHash(): String? {
        return localIdentityDao.getActiveIdentity()?.identityHash
    }

    /**
     * Get all conversations synchronously (for testing/debugging).
     */
    suspend fun getConversationsSync(): List<Conversation> {
        val activeIdentity = localIdentityDao.getActiveIdentity()
            ?: return emptyList()
        return conversationDao.getConversations(activeIdentity.identityHash)
            .map { it.toConversation() }
    }

    /**
     * Get all messages for a conversation synchronously (for testing/debugging).
     */
    suspend fun getMessagesSync(peerHash: String): List<Message> {
        val activeIdentity = localIdentityDao.getActiveIdentity()
            ?: return emptyList()
        return messageDao.getMessagesForConversation(peerHash, activeIdentity.identityHash)
            .map { it.toMessage() }
    }

    private fun ConversationEntity.toConversation(): Conversation {
        return Conversation(
            peerHash = peerHash,
            peerName = peerName,
            displayName = peerName,
            peerPublicKey = peerPublicKey,
            lastMessage = lastMessage,
            lastMessageTimestamp = lastMessageTimestamp,
            unreadCount = unreadCount,
            lastSeenTimestamp = lastSeenTimestamp,
        )
    }

    private fun MessageEntity.toMessage(): Message {
        return Message(
            id = id,
            destinationHash = conversationHash,
            content = content,
            timestamp = timestamp,
            isFromMe = isFromMe,
            status = status,
            isRead = isRead,
            fieldsJson = fieldsJson,
            deliveryMethod = deliveryMethod,
            errorMessage = errorMessage,
            replyToMessageId = replyToMessageId,
            receivedHopCount = receivedHopCount,
            receivedInterface = receivedInterface,
            receivedRssi = receivedRssi,
            receivedSnr = receivedSnr,
            receivedAt = receivedAt,
            sentInterface = sentInterface,
        )
    }
}
