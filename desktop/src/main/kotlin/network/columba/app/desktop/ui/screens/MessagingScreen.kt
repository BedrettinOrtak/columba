package network.columba.app.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.java.KoinJavaComponent.getKoin
import network.columba.app.desktop.ui.viewmodel.MessagingViewModel
import network.columba.app.desktop.i18n.Strings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    var pendingAttachments by remember { mutableStateOf<List<Pair<String, ByteArray>>>(emptyList()) }
    var pendingAudio by remember { mutableStateOf<Pair<Int, ByteArray>?>(null) }
    val recordingState = remember { VoiceRecordingState() }

    // New-conversation dialog state
    var showNewConversationDialog by remember { mutableStateOf(false) }
    var newPeerHash by remember { mutableStateOf("") }
    var newPeerName by remember { mutableStateOf("") }
    var newPeerError by remember { mutableStateOf<String?>(null) }

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
                    FilledTonalIconButton(onClick = {
                        newPeerHash = ""
                        newPeerName = ""
                        newPeerError = null
                        showNewConversationDialog = true
                    }) {
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
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            // Pending attachment chips
                            if (pendingAttachments.isNotEmpty() || pendingAudio != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    pendingAttachments.forEachIndexed { idx, (name, bytes) ->
                                        AssistChip(
                                            onClick = {
                                                pendingAttachments = pendingAttachments
                                                    .toMutableList()
                                                    .apply { removeAt(idx) }
                                            },
                                            label = { Text("$name (${bytes.size}B)") },
                                            leadingIcon = {
                                                Icon(Icons.Default.AttachFile, contentDescription = null)
                                            },
                                            trailingIcon = {
                                                Icon(Icons.Default.Close, contentDescription = "Remove")
                                            },
                                        )
                                    }
                                    pendingAudio?.let { (_, bytes) ->
                                        AssistChip(
                                            onClick = { pendingAudio = null },
                                            label = { Text("Voice (${bytes.size}B)") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Audiotrack, contentDescription = null)
                                            },
                                            trailingIcon = {
                                                Icon(Icons.Default.Close, contentDescription = "Remove")
                                            },
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // File attach
                                IconButton(onClick = {
                                    val files = pickFiles()
                                    if (files.isNotEmpty()) {
                                        pendingAttachments = pendingAttachments + files
                                    }
                                }) {
                                    Icon(Icons.Default.AttachFile, contentDescription = "Attach files")
                                }
                                // Voice record toggle
                                IconButton(onClick = {
                                    if (recordingState.isRecording) {
                                        val captured = recordingState.stop()
                                        if (captured != null && captured.isNotEmpty()) {
                                            // Codec 0 = raw PCM/wav container; mirrors Android's audio field.
                                            pendingAudio = 0 to captured
                                        }
                                    } else {
                                        recordingState.start()
                                    }
                                }) {
                                    if (recordingState.isRecording) {
                                        Icon(
                                            Icons.Default.MicOff,
                                            contentDescription = "Stop recording",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    } else {
                                        Icon(Icons.Default.Mic, contentDescription = "Record voice")
                                    }
                                }
                                OutlinedTextField(
                                    value = messageText,
                                    onValueChange = { messageText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text(strings.typeMessage) },
                                    maxLines = 4
                                )
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.sendMessage(
                                            content = messageText,
                                            attachments = pendingAttachments,
                                            audio = pendingAudio,
                                        )
                                        messageText = ""
                                        pendingAttachments = emptyList()
                                        pendingAudio = null
                                    },
                                    enabled = messageText.isNotBlank() ||
                                        pendingAttachments.isNotEmpty() ||
                                        pendingAudio != null,
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.send)
                                }
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

    if (showNewConversationDialog) {
        AlertDialog(
            onDismissRequest = { showNewConversationDialog = false },
            title = { Text(strings.newConversation) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPeerHash,
                        onValueChange = {
                            newPeerHash = it.trim()
                            newPeerError = null
                        },
                        label = { Text("Peer hash (32 hex)") },
                        placeholder = { Text("e.g. 4f3a8b...") },
                        singleLine = true,
                        isError = newPeerError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPeerName,
                        onValueChange = { newPeerName = it },
                        label = { Text(strings.displayName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (newPeerError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = newPeerError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = viewModel.startConversation(newPeerHash, newPeerName)
                    if (ok) {
                        showNewConversationDialog = false
                    } else {
                        newPeerError = "Invalid hash. Need 32 hex chars (16 bytes)."
                    }
                }) {
                    Text(strings.create)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewConversationDialog = false }) {
                    Text(strings.cancel)
                }
            },
        )
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
    val attachments = remember(message.fieldsJson) { parseAttachments(message.fieldsJson) }
    val audio = remember(message.fieldsJson) { parseAudio(message.fieldsJson) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isFromMe) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // Attachment list
                if (attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    attachments.forEach { att ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        att.filename,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "${att.bytes.size} B",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                IconButton(onClick = { saveBytesToFile(att.filename, att.bytes) }) {
                                    Icon(Icons.Default.SaveAlt, contentDescription = "Save")
                                }
                            }
                        }
                    }
                }

                // Voice playback
                if (audio != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(onClick = { playAudioBytes(audio) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                            }
                            Text("Voice (${audio.size} B)", modifier = Modifier.weight(1f))
                            IconButton(onClick = { saveBytesToFile("voice.wav", audio) }) {
                                Icon(Icons.Default.SaveAlt, contentDescription = "Save")
                            }
                        }
                    }
                }

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

// ============================================================================
// Attachment helpers (JVM-only)
// ============================================================================

internal data class ParsedAttachment(val filename: String, val bytes: ByteArray)

private val attachmentJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseAttachments(fieldsJson: String?): List<ParsedAttachment> {
    if (fieldsJson.isNullOrBlank()) return emptyList()
    return try {
        val root = attachmentJson.parseToJsonElement(fieldsJson) as? JsonObject ?: return emptyList()
        val arr = root["5"] as? JsonArray ?: return emptyList()
        arr.mapNotNull { entry ->
            when (entry) {
                is JsonObject -> {
                    val name = entry["filename"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val hex = entry["data"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    ParsedAttachment(name, hexToBytesUi(hex))
                }
                is JsonArray -> {
                    if (entry.size < 2) return@mapNotNull null
                    val name = entry[0].jsonPrimitive.contentOrNull ?: return@mapNotNull null
                    val hex = entry[1].jsonPrimitive.contentOrNull ?: return@mapNotNull null
                    ParsedAttachment(name, hexToBytesUi(hex))
                }
                else -> null
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }
}

internal fun parseAudio(fieldsJson: String?): ByteArray? {
    if (fieldsJson.isNullOrBlank()) return null
    return try {
        val root = attachmentJson.parseToJsonElement(fieldsJson) as? JsonObject ?: return null
        val arr = root["7"] as? JsonArray ?: return null
        if (arr.size < 2) return null
        val hex = arr[1].jsonPrimitive.contentOrNull ?: return null
        hexToBytesUi(hex)
    } catch (_: Throwable) {
        null
    }
}

private fun hexToBytesUi(hex: String): ByteArray {
    val clean = hex.trim()
    if (clean.length % 2 != 0) return ByteArray(0)
    val out = ByteArray(clean.length / 2)
    var i = 0
    while (i < clean.length) {
        out[i / 2] = ((Character.digit(clean[i], 16) shl 4) or Character.digit(clean[i + 1], 16)).toByte()
        i += 2
    }
    return out
}

internal fun pickFiles(): List<Pair<String, ByteArray>> {
    val chooser = javax.swing.JFileChooser().apply {
        isMultiSelectionEnabled = true
        dialogTitle = "Attach files"
    }
    val result = chooser.showOpenDialog(null)
    if (result != javax.swing.JFileChooser.APPROVE_OPTION) return emptyList()
    return chooser.selectedFiles.orEmpty().mapNotNull { f ->
        try {
            f.name to java.nio.file.Files.readAllBytes(f.toPath())
        } catch (_: Throwable) {
            null
        }
    }
}

internal fun saveBytesToFile(suggestedName: String, bytes: ByteArray) {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = "Save attachment"
        selectedFile = java.io.File(suggestedName)
    }
    val result = chooser.showSaveDialog(null)
    if (result != javax.swing.JFileChooser.APPROVE_OPTION) return
    try {
        java.nio.file.Files.write(chooser.selectedFile.toPath(), bytes)
    } catch (_: Throwable) {
        // Ignore — best effort save.
    }
}

internal fun playAudioBytes(bytes: ByteArray) {
    Thread({
        try {
            // Try as a known audio container first (WAV/AU/AIFF).
            val ais = try {
                javax.sound.sampled.AudioSystem.getAudioInputStream(java.io.ByteArrayInputStream(bytes))
            } catch (_: Throwable) {
                // Fallback: treat as raw 16 kHz/16-bit/mono PCM (matches our recorder).
                val fmt = javax.sound.sampled.AudioFormat(16000f, 16, 1, true, false)
                javax.sound.sampled.AudioInputStream(
                    java.io.ByteArrayInputStream(bytes),
                    fmt,
                    (bytes.size / fmt.frameSize).toLong(),
                )
            }
            val clip = javax.sound.sampled.AudioSystem.getClip()
            clip.open(ais)
            clip.start()
        } catch (_: Throwable) {
            // Best-effort playback.
        }
    }, "voice-playback").apply { isDaemon = true }.start()
}

// ============================================================================
// Voice recording (JVM, javax.sound.sampled). Produces a WAV byte array.
// ============================================================================

internal class VoiceRecordingState {
    var isRecording: Boolean by mutableStateOf(false)
        private set

    private var line: javax.sound.sampled.TargetDataLine? = null
    private var thread: Thread? = null
    private var buffer: java.io.ByteArrayOutputStream? = null
    private val format = javax.sound.sampled.AudioFormat(16000f, 16, 1, true, false)

    fun start() {
        if (isRecording) return
        try {
            val info = javax.sound.sampled.DataLine.Info(javax.sound.sampled.TargetDataLine::class.java, format)
            if (!javax.sound.sampled.AudioSystem.isLineSupported(info)) return
            val l = javax.sound.sampled.AudioSystem.getLine(info) as javax.sound.sampled.TargetDataLine
            l.open(format)
            l.start()
            line = l
            val out = java.io.ByteArrayOutputStream()
            buffer = out
            isRecording = true
            thread = Thread({
                val buf = ByteArray(4096)
                try {
                    while (isRecording) {
                        val n = l.read(buf, 0, buf.size)
                        if (n > 0) out.write(buf, 0, n)
                    }
                } catch (_: Throwable) {
                    // Recording aborted.
                }
            }, "voice-recorder").apply { isDaemon = true }
            thread?.start()
        } catch (_: Throwable) {
            cleanup()
        }
    }

    fun stop(): ByteArray? {
        if (!isRecording) return null
        isRecording = false
        try {
            line?.stop()
            line?.close()
            thread?.join(500)
        } catch (_: Throwable) {
            // Ignore.
        }
        val pcm = buffer?.toByteArray()
        cleanup()
        if (pcm == null || pcm.isEmpty()) return null
        return wrapPcmAsWav(pcm, format)
    }

    private fun cleanup() {
        line = null
        thread = null
        buffer = null
    }

    private fun wrapPcmAsWav(
        pcm: ByteArray,
        fmt: javax.sound.sampled.AudioFormat,
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val ais = javax.sound.sampled.AudioInputStream(
            java.io.ByteArrayInputStream(pcm),
            fmt,
            (pcm.size / fmt.frameSize).toLong(),
        )
        try {
            javax.sound.sampled.AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, out)
        } catch (_: Throwable) {
            return pcm
        }
        return out.toByteArray()
    }
}
