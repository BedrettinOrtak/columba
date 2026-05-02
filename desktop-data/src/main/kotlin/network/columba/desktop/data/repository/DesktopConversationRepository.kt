package network.columba.desktop.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import network.columba.desktop.data.db.ColumbaDatabase
import network.columba.desktop.data.db.dao.ConversationDao
import network.columba.desktop.data.db.dao.LocalIdentityDao
import network.columba.desktop.data.db.dao.MessageDao
import network.columba.desktop.data.db.entity.ConversationEntity
import network.columba.desktop.data.db.entity.MessageEntity
import network.columba.desktop.data.reticulum.DesktopReticulumService
import network.columba.shared.domain.model.Conversation
import network.columba.shared.domain.model.Message
import network.columba.shared.domain.repository.ConversationRepository
import network.reticulum.lxmf.LXMessage
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.UUID

/**
 * Desktop implementation of ConversationRepository.
 *
 * Persists messages locally to SQLite and routes outbound messages through
 * the Reticulum LXMF stack so they reach the network. Inbound LXMF messages
 * delivered by [DesktopReticulumService] are written back to the same SQLite
 * tables so the UI's reactive flows pick them up automatically.
 */
class DesktopConversationRepository(
    private val database: ColumbaDatabase,
    private val reticulumService: DesktopReticulumService? = null,
) : ConversationRepository {
    private val logger = LoggerFactory.getLogger(DesktopConversationRepository::class.java)
    private val conversationDao = ConversationDao { database.getConnection() }
    private val messageDao = MessageDao { database.getConnection() }
    private val localIdentityDao = LocalIdentityDao { database.getConnection() }

    /** Bumped on every write so observers re-query the DAO. */
    private val refreshTrigger = MutableStateFlow(0L)

    private val inboundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Subscribe to inbound LXMF messages from the Reticulum service. Each
        // delivered message gets persisted as if the user had received it via
        // any other transport — the UI flow then surfaces it without any
        // additional plumbing.
        reticulumService?.let { svc ->
            inboundScope.launch {
                svc.inbound.collect { lxmf ->
                    runCatching { ingestInbound(lxmf) }
                        .onFailure { e -> logger.warn("Failed to ingest inbound LXMF", e) }
                }
            }
        }
    }

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

        // For outgoing messages, hand off to the Reticulum LXMF router so the
        // peer actually gets it. We only do this for messages we authored
        // (incoming ones come from the network already).
        if (message.isFromMe) {
            reticulumService?.let { svc ->
                runCatching {
                    val recipientBytes = hexStringToBytes(peerHash)
                    val sentHash = svc.sendMessage(recipientBytes, message.content)
                    logger.info(
                        "Sent LXMF message to {} (router hash={})",
                        peerHash,
                        sentHash.joinToString("") { "%02x".format(it) },
                    )
                }.onFailure { e ->
                    logger.warn("Failed to dispatch outbound LXMF to {}: {}", peerHash, e.message)
                    // Mark the persisted message as failed so the UI can show it.
                    runCatching {
                        messageDao.updateMessageStatus(
                            message.id,
                            localIdentityDao.getActiveIdentity()?.identityHash ?: return@runCatching,
                            "failed",
                        )
                        notifyChanged()
                    }
                }
            }
        }
    }

    /**
     * Persist an inbound LXMF message. Called from the Reticulum service
     * subscription. Source hash becomes the conversation key.
     */
    private suspend fun ingestInbound(lxmf: LXMessage) {
        val sourceHash = lxmf.sourceHash ?: return
        val peerHash = sourceHash.joinToString("") { "%02x".format(it) }
        val activeIdentity = localIdentityDao.getActiveIdentity() ?: return
        val identityHash = activeIdentity.identityHash
        val messageId = lxmf.hash?.joinToString("") { "%02x".format(it) }
            ?: UUID.randomUUID().toString()
        val timestampMs = (lxmf.timestamp?.let { (it * 1000).toLong() })
            ?: System.currentTimeMillis()
        val content = lxmf.content ?: ""
        // Best-effort peer name. Source-side announces populate this elsewhere;
        // for first contact we fall back to a hash prefix so the user sees
        // something rather than a blank row.
        val peerName = peerHash.take(12)

        database.withConnection { _ ->
            if (messageDao.messageExists(messageId, identityHash)) return@withConnection
            val existing = conversationDao.getConversation(peerHash, identityHash)
            if (existing != null) {
                conversationDao.updateConversation(
                    existing.copy(
                        lastMessage = content,
                        lastMessageTimestamp = timestampMs,
                        unreadCount = existing.unreadCount + 1,
                    ),
                )
            } else {
                conversationDao.insertConversation(
                    ConversationEntity(
                        peerHash = peerHash,
                        identityHash = identityHash,
                        peerName = peerName,
                        peerPublicKey = null,
                        lastMessage = content,
                        lastMessageTimestamp = timestampMs,
                        unreadCount = 1,
                    ),
                )
            }
            messageDao.insertMessage(
                MessageEntity(
                    id = messageId,
                    conversationHash = peerHash,
                    identityHash = identityHash,
                    content = content,
                    timestamp = timestampMs,
                    isFromMe = false,
                    status = "delivered",
                    isRead = false,
                    receivedAt = System.currentTimeMillis(),
                ),
            )
        }
        notifyChanged()
    }

    private fun hexStringToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x").lowercase()
        require(clean.length % 2 == 0) { "Hex string must have even length: $hex" }
        return ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
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
