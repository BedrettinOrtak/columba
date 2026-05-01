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

    fun sendMessage(content: String) {
        val conversation = _selectedConversation.value ?: return
        if (content.isBlank()) return

        scope.launch {
            try {
                val message = Message(
                    id = UUID.randomUUID().toString(),
                    destinationHash = conversation.peerHash,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    isFromMe = true,
                    status = "sent",
                    receivedAt = System.currentTimeMillis(),
                )

                conversationRepository.saveMessage(
                    peerHash = conversation.peerHash,
                    peerName = conversation.peerName,
                    message = message,
                    peerPublicKey = conversation.peerPublicKey,
                )
                // Reactive flows pick up changes automatically.
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
