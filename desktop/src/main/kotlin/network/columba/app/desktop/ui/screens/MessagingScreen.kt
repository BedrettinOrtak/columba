package network.columba.app.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.java.KoinJavaComponent.getKoin
import network.columba.app.desktop.ui.viewmodel.MessagingViewModel
import network.columba.app.desktop.i18n.Strings

@Composable
fun MessagingScreen(modifier: Modifier = Modifier) {
    val viewModel: MessagingViewModel = remember { MessagingViewModel() }
    val conversations by viewModel.conversations.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val selectedConversation by viewModel.selectedConversation.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val strings by Strings.stringsState.collectAsState()

    // State for message input
    var messageText by remember { mutableStateOf("") }

    Row(modifier = modifier.fillMaxSize()) {
        // Conversation List
        Card(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.messagesTab,
                        style = MaterialTheme.typography.titleLarge
                    )
                    FilledTonalIconButton(onClick = { /* TODO: New conversation */ }) {
                        Icon(Icons.Default.Add, contentDescription = strings.newConversation)
                    }
                }
                HorizontalDivider()

                // Conversation List
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        state = rememberLazyListState(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        if (conversations.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = strings.noConversations,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(conversations) { conversation ->
                                ConversationItem(
                                    conversation = conversation,
                                    isSelected = selectedConversation?.peerHash == conversation.peerHash,
                                    onClick = {
                                        viewModel.selectConversation(conversation)
                                    },
                                    onMarkRead = {
                                        viewModel.markAsRead(conversation.peerHash)
                                    },
                                    strings = strings
                                )
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider()

        // Chat Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (selectedConversation != null) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Chat Header
                    Surface(shadowElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = selectedConversation!!.displayName,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = if (selectedConversation!!.unreadCount > 0) {
                                        "${selectedConversation!!.unreadCount} ${strings.unread}"
                                    } else {
                                        selectedConversation!!.peerHash.take(16) + "..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                viewModel.deleteConversation(selectedConversation!!.peerHash)
                            }) {
                                Text(strings.delete)
                            }
                        }
                    }

                    // Messages
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        state = rememberLazyListState(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { message ->
                            MessageBubble(message = message, strings = strings)
                        }
                    }

                    // Input
                    Surface(shadowElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(strings.typeMessage) },
                                maxLines = 4
                            )
                            FilledTonalButton(
                                onClick = {
                                    viewModel.sendMessage(messageText)
                                    messageText = ""
                                },
                                enabled = messageText.isNotBlank()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.send)
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = strings.selectConversation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: network.columba.shared.domain.model.Conversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onMarkRead: () -> Unit,
    strings: Strings
) {
    Surface(
        onClick = {
            onClick()
            if (conversation.unreadCount > 0) {
                onMarkRead()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
               else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = conversation.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                if (conversation.unreadCount > 0) {
                    Badge {
                        Text(conversation.unreadCount.toString())
                    }
                }
            }
            Text(
                text = conversation.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MessageBubble(message: network.columba.shared.domain.model.Message, strings: Strings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isFromMe) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSecondaryContainer
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimestamp(message.timestamp, strings),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.isFromMe)
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    if (message.isFromMe) {
                        Text(
                            text = getStatusText(message.status, strings),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (message.isFromMe)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                else
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long, strings: Strings): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> strings.justNow
        diff < 3600_000 -> Strings.formatTimeAgo(strings.minutesAgo, (diff / 60_000).toInt())
        diff < 86_400_000 -> Strings.formatTimeAgo(strings.hoursAgo, (diff / 3_600_000).toInt())
        else -> Strings.formatTimeAgo(strings.daysAgo, (diff / 86_400_000).toInt())
    }
}

private fun getStatusText(status: String, strings: Strings): String {
    return when (status) {
        "sent" -> strings.statusSent
        "delivered" -> strings.statusDelivered
        "pending" -> strings.statusPending
        "failed" -> strings.statusFailed
        else -> status
    }
}
