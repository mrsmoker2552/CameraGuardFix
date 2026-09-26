package com.boss.cameraguard.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.boss.cameraguard.chat.*
import com.boss.cameraguard.ui.theme.CameraGuardPalette
import com.boss.cameraguard.ui.theme.neumorphicRaised
import com.boss.cameraguard.ui.theme.neumorphicInset
import kotlinx.coroutines.launch
import java.text.DateFormat

/**
 * One conversation thread: text/image/voice messages, composer, and the safety-relevant
 * block/report/leave controls. This screen is only ever reached through the dedicated Chat
 * tab or "Message this rider" - never overlaid on the driving HUD (see ChatHostScreen).
 */
@OptIn(ExperimentalMaterial3Api::class) // TopAppBar is still experimental in Material3
@Composable
fun ChatThreadScreen(
    modifier: Modifier = Modifier,
    myUid: String,
    myDisplayName: String,
    conversation: Conversation,
    onBack: () -> Unit,
    onOpenGroupInfo: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember(conversation.id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var hasMoreOlder by remember(conversation.id) { mutableStateOf(true) }
    var loadingOlder by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var iHaveBlocked by remember { mutableStateOf(false) }
    var otherProfile by remember { mutableStateOf<ChatProfile?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    val voicePlayer = rememberVoicePlayer(scope)
    val recorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordElapsedMs by remember { mutableStateOf(0L) }

    val otherUid = conversation.otherUid(myUid)
    val isGroup = conversation.type == ConversationType.GROUP
    val title = conversation.groupName ?: otherProfile?.displayName ?: "Rider"

    DisposableEffect(conversation.id) {
        ChatUiState.openConversationId.value = conversation.id
        onDispose { ChatUiState.openConversationId.value = null }
    }

    LaunchedEffect(conversation.id) {
        ChatRepository.observeLatestMessages(conversation.id).collect {
            messages = it
            ChatRepository.markRead(conversation.id)
        }
    }
    LaunchedEffect(otherUid) {
        if (otherUid != null) {
            otherProfile = ChatRepository.getChatProfile(otherUid)
            launch { ChatRepository.observeIHaveBlocked(otherUid).collect { iHaveBlocked = it } }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            sending = true
            lastError = null
            try {
                ChatRepository.sendImage(conversation.id, uri, context)
            } catch (e: ChatBlockedException) {
                lastError = e.message
            } catch (e: Exception) {
                lastError = "Photo failed to send. Check your connection and try again."
            } finally {
                sending = false
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching { recorder.start() }.onSuccess { isRecording = true }
        } else {
            lastError = "Microphone permission is needed to record a voice note."
        }
    }

    fun startRecording() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            runCatching { recorder.start() }.onSuccess { isRecording = true }
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            recordElapsedMs = recorder.elapsedMs()
            kotlinx.coroutines.delay(200)
        }
    }

    fun finishRecording(send: Boolean) {
        isRecording = false
        val file = recorder.stopAndFinish()
        if (!send || file == null) {
            recorder.deleteOutputFile()
            return
        }
        val durationMs = recordElapsedMs
        scope.launch {
            sending = true
            lastError = null
            try {
                ChatRepository.sendVoice(conversation.id, file, durationMs)
            } catch (e: ChatBlockedException) {
                lastError = e.message
            } catch (e: Exception) {
                lastError = "Voice note failed to send. Check your connection and try again."
            } finally {
                sending = false
                recorder.deleteOutputFile()
            }
        }
    }

    fun sendText() {
        val toSend = text
        if (toSend.isBlank()) return
        text = ""
        scope.launch {
            sending = true
            lastError = null
            try {
                ChatRepository.sendText(conversation.id, toSend)
            } catch (e: ChatBlockedException) {
                lastError = e.message
                text = toSend
            } catch (e: Exception) {
                lastError = "Message failed to send. Tap to retry."
                text = toSend
            } finally {
                sending = false
            }
        }
    }

    Scaffold(
        containerColor = CameraGuardPalette.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        Modifier.clickable(enabled = isGroup, onClick = onOpenGroupInfo),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isGroup) Box(Modifier.size(34.dp).background(CameraGuardPalette.AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = CameraGuardPalette.Accent, modifier = Modifier.size(18.dp))
                        } else RiderAvatar(title, size = 34.dp)
                        Column {
                            Text(title, color = CameraGuardPalette.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (isGroup) Text("${conversation.memberUids.size} members", color = CameraGuardPalette.Muted, fontSize = 10.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CameraGuardPalette.Text) }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More", tint = CameraGuardPalette.Text) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (isGroup) {
                            DropdownMenuItem(text = { Text("Group info") }, onClick = { showMenu = false; onOpenGroupInfo() })
                            DropdownMenuItem(text = { Text("Leave group") }, onClick = { showMenu = false; showLeaveConfirm = true })
                        } else {
                            DropdownMenuItem(
                                text = { Text(if (iHaveBlocked) "Unblock rider" else "Block rider") },
                                onClick = { showMenu = false; if (iHaveBlocked) scope.launch { ChatRepository.unblockUser(otherUid!!) } else showBlockConfirm = true }
                            )
                            DropdownMenuItem(text = { Text("Report") }, onClick = { showMenu = false; showReportDialog = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CameraGuardPalette.Surface)
            )
        },
        bottomBar = {
            Column {
                lastError?.let {
                    CannotSendBanner(it)
                }
                if (!isGroup && iHaveBlocked) {
                    BlockedBanner(title)
                } else {
                    ChatComposer(
                        text = text, onTextChange = { text = it }, onSend = ::sendText,
                        sending = sending, isRecording = isRecording, recordElapsedMs = recordElapsedMs,
                        onPickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onStartRecording = ::startRecording,
                        onCancelRecording = { finishRecording(send = false) },
                        onSendRecording = { finishRecording(send = true) }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            reverseLayout = false
        ) {
            if (hasMoreOlder && messages.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = {
                            if (loadingOlder) return@TextButton
                            loadingOlder = true
                            scope.launch {
                                val older = ChatRepository.loadOlderMessages(conversation.id, messages.first())
                                if (older.isEmpty()) hasMoreOlder = false
                                messages = older + messages
                                loadingOlder = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (loadingOlder) "Loading…" else "Load earlier messages", color = CameraGuardPalette.Accent, fontSize = 12.sp) }
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageRow(message = message, mine = message.senderUid == myUid, player = voicePlayer, onOpenImage = { fullScreenImageUrl = it })
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val headerOffset = if (hasMoreOlder) 1 else 0
            listState.animateScrollToItem(messages.lastIndex + headerOffset)
        }
    }

    fullScreenImageUrl?.let { url -> FullScreenImageViewer(url = url, onDismiss = { fullScreenImageUrl = null }) }

    if (showBlockConfirm && otherUid != null) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Block $title?") },
            text = { Text("They won't be able to send you private messages anymore. You can unblock them anytime from this menu.") },
            confirmButton = {
                TextButton(onClick = { showBlockConfirm = false; scope.launch { ChatRepository.blockUser(otherUid) } }) { Text("Block", color = CameraGuardPalette.Danger) }
            },
            dismissButton = { TextButton(onClick = { showBlockConfirm = false }) { Text("Cancel") } },
            containerColor = CameraGuardPalette.Surface, titleContentColor = CameraGuardPalette.Text, textContentColor = CameraGuardPalette.Muted
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave this group?") },
            text = { Text("You'll stop receiving messages from $title unless someone adds you back.") },
            confirmButton = {
                TextButton(onClick = { showLeaveConfirm = false; scope.launch { ChatRepository.leaveGroup(conversation.id) }; onBack() }) { Text("Leave", color = CameraGuardPalette.Danger) }
            },
            dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") } },
            containerColor = CameraGuardPalette.Surface, titleContentColor = CameraGuardPalette.Text, textContentColor = CameraGuardPalette.Muted
        )
    }

    if (showReportDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report $title") },
            text = {
                Column {
                    Text("Tell us what happened. This is sent for review - it doesn't block the rider automatically.", color = CameraGuardPalette.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("What happened?") })
                }
            },
            confirmButton = {
                TextButton(enabled = reason.isNotBlank(), onClick = {
                    showReportDialog = false
                    scope.launch { ChatRepository.reportUser(otherUid ?: conversation.id, conversation.id, null, reason) }
                }) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text("Cancel") } },
            containerColor = CameraGuardPalette.Surface, titleContentColor = CameraGuardPalette.Text, textContentColor = CameraGuardPalette.Muted
        )
    }
}

@Composable
private fun MessageRow(message: ChatMessage, mine: Boolean, player: com.boss.cameraguard.chat.VoicePlayer, onOpenImage: (String) -> Unit) {
    val timeLabel = message.sentAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(it) } ?: "Sending…"
    val statusLabel = if (mine) (if (message.sentAt != null) "Sent" else null) else null
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(Modifier.widthIn(max = 280.dp)) {
            when (message.type) {
                MessageType.TEXT -> TextMessageBubble(message.text.orEmpty(), mine, timeLabel, statusLabel)
                MessageType.IMAGE -> message.mediaUrl?.let { ImageMessageBubble(it, mine, timeLabel, statusLabel) { onOpenImage(it) } }
                MessageType.VOICE -> message.mediaUrl?.let { VoiceMessageBubble(message.id, it, message.mediaDurationMs ?: 0L, mine, timeLabel, statusLabel, player) }
                MessageType.SYSTEM -> Text(message.text.orEmpty(), color = CameraGuardPalette.Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ChatComposer(
    text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, sending: Boolean,
    isRecording: Boolean, recordElapsedMs: Long,
    onPickImage: () -> Unit, onStartRecording: () -> Unit, onCancelRecording: () -> Unit, onSendRecording: () -> Unit
) {
    Surface(color = CameraGuardPalette.Background, tonalElevation = 0.dp) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
            if (!isRecording) QuickEmojiRow(onPick = { onTextChange(text + it) })
            Spacer(Modifier.height(6.dp))
            if (isRecording) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = CameraGuardPalette.Danger)
                    Text(formatDurationShort(recordElapsedMs), color = CameraGuardPalette.Text, fontWeight = FontWeight.Bold)
                    Text("Recording…", color = CameraGuardPalette.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancelRecording) { Text("Cancel", color = CameraGuardPalette.Muted) }
                    FilledIconButton(onClick = onSendRecording, colors = IconButtonDefaults.filledIconButtonColors(containerColor = CameraGuardPalette.Accent)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send voice note", tint = CameraGuardPalette.Background)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onPickImage) { Icon(Icons.Default.Image, contentDescription = "Send photo", tint = CameraGuardPalette.Muted) }
                    OutlinedTextField(
                        value = text, onValueChange = onTextChange,
                        modifier = Modifier.weight(1f).neumorphicInset(RoundedCornerShape(20.dp), CameraGuardPalette.Raised), placeholder = { Text("Message…") },
                        maxLines = 5, shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CameraGuardPalette.Accent.copy(alpha=.55f), unfocusedBorderColor = Color.Transparent, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                    )
                    if (text.isBlank()) {
                        IconButton(onClick = onStartRecording) { Icon(Icons.Default.Mic, contentDescription = "Record voice note", tint = CameraGuardPalette.Muted) }
                    } else {
                        FilledIconButton(onClick = onSend, enabled = !sending, colors = IconButtonDefaults.filledIconButtonColors(containerColor = CameraGuardPalette.Accent)) {
                            if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CameraGuardPalette.Background)
                            else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = CameraGuardPalette.Background)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenImageViewer(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            coil3.compose.AsyncImage(model = url, contentDescription = "Photo", modifier = Modifier.fillMaxWidth())
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
