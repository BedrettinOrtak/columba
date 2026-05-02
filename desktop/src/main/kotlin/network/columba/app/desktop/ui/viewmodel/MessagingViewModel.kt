package network.columba.app.desktop.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import network.columba.shared.domain.model.Conversation
import network.columba.shared.domain.model.Message
import network.columba.shared.domain.repository.ConversationRepository
import org.koin.java.KoinJavaComponent.getKoin
import java.util.UUID

/**
 * ViewModel for messaging screen (Desktop version without Android lifecycle).
 * Manages conversations and messages using the repository.
 */
class MessagingViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val conversationRepository: ConversationRepository =
        getKoin().get<ConversationRepository>()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _selectedConversation = MutableStateFlow<Conversation?>(null)
    val selectedConversation: StateFlow<Conversation?> = _selectedConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        observeConversations()
        observeMessages()
    }

    private fun observeConversations() {
        scope.launch {
            _isLoading.value = true
            try {
                conversationRepository.getConversations().collect { list ->
                    _conversations.value = list
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeMessages() {
        scope.launch {
            try {
                _selectedConversation
                    .flatMapLatest { conversation ->
                        if (conversation == null) {
                            flowOf(emptyList())
                        } else {
                            conversationRepository.getMessages(conversation.peerHash)
                        }
                    }
                    .collect { list ->
                        _messages.value = list
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadConversations() {
        // No-op: reactive flow handles updates.
    }

    fun selectConversation(conversation: Conversation) {
        _selectedConversation.value = conversation
    }

    /**
     * Start a new conversation with a peer identified by their 16-byte
     * destination hash (32-char hex). Creates an empty conversation row in
     * the database and selects it. Returns true on success, false if the
     * hash is malformed.
     */
    fun startConversation(peerHashHex: String, peerName: String): Boolean {
        val cleaned = peerHashHex.trim().removePrefix("0x").lowercase()
        if (cleaned.length != 32 || !cleaned.all { it in '0'..'9' || it in 'a'..'f' }) {
            return false
        }
        val displayName = peerName.trim().ifBlank { cleaned.take(12) }
        scope.launch {
            try {
                // Persist a placeholder message-less conversation by saving an
                // empty inbound-style row (saveMessage with isFromMe=true would
                // actually transmit). We use a dedicated bootstrap path: save a
                // zero-content outbound that won't dispatch (status pending,
                // empty content) — simpler is to just call saveMessage with a
                // synthetic system message. To avoid noise, we instead select
                // the conversation locally and let the first real send create
                // the DB row.
                val placeholder = Conversation(
                    peerHash = cleaned,
                    peerName = displayName,
                    displayName = displayName,
                    peerPublicKey = null,
                    lastMessage = "",
                    lastMessageTimestamp = System.currentTimeMillis(),
                    unreadCount = 0,
                )
                _selectedConversation.value = placeholder
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return true
    }

    fun sendMessage(content: String) {
        sendMessage(content, attachments = emptyList(), audio = null)
    }

    /**
     * Send a message with optional file attachments and/or a single voice note.
     *
     * @param attachments list of (filename, fileBytes). Stored under LXMF
     *   field 5 (FIELD_FILE_ATTACHMENTS) on the wire.
     * @param audio optional pair of (codecId, audioBytes). Codec 0 is the
     *   default opus/raw container; matches Sideband's audio field 7.
     */
    fun sendMessage(
        content: String,
        attachments: List<Pair<String, ByteArray>>,
        audio: Pair<Int, ByteArray>?,
    ) {
        val conversation = _selectedConversation.value ?: return
        // Allow blank content if there's an attachment (matches Android UX).
        if (content.isBlank() && attachments.isEmpty() && audio == null) return

        scope.launch {
            try {
                val fieldsJson = buildFieldsJson(attachments, audio)
                val message = Message(
                    id = UUID.randomUUID().toString(),
                    destinationHash = conversation.peerHash,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    isFromMe = true,
                    status = "sent",
                    fieldsJson = fieldsJson,
                    receivedAt = System.currentTimeMillis(),
                )

                conversationRepository.saveMessage(
                    peerHash = conversation.peerHash,
                    peerName = conversation.peerName,
                    message = message,
                    peerPublicKey = conversation.peerPublicKey,
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildFieldsJson(
        attachments: List<Pair<String, ByteArray>>,
        audio: Pair<Int, ByteArray>?,
    ): String? {
        if (attachments.isEmpty() && audio == null) return null
        val sb = StringBuilder()
        sb.append("{")
        var first = true
        if (attachments.isNotEmpty()) {
            sb.append("\"5\":[")
            attachments.forEachIndexed { i, (name, data) ->
                if (i > 0) sb.append(',')
                val safeName = name
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                sb.append("{\"filename\":\"")
                sb.append(safeName)
                sb.append("\",\"data\":\"")
                sb.append(data.joinToString("") { "%02x".format(it) })
                sb.append("\",\"size\":")
                sb.append(data.size)
                sb.append('}')
            }
            sb.append(']')
            first = false
        }
        if (audio != null) {
            if (!first) sb.append(',')
            sb.append("\"7\":[")
            sb.append(audio.first)
            sb.append(",\"")
            sb.append(audio.second.joinToString("") { "%02x".format(it) })
            sb.append("\"]")
        }
        sb.append('}')
        return sb.toString()
    }

    fun markAsRead(peerHash: String) {
        scope.launch {
            try {
                conversationRepository.markConversationAsRead(peerHash)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteConversation(peerHash: String) {
        scope.launch {
            try {
                conversationRepository.deleteConversation(peerHash)
                if (_selectedConversation.value?.peerHash == peerHash) {
                    _selectedConversation.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cleanup() {
        (scope.coroutineContext[Job] as? Job)?.cancel()
    }
}
