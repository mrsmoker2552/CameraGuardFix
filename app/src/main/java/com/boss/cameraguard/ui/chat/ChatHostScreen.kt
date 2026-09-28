package com.boss.cameraguard.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.boss.cameraguard.chat.ChatBlockedException
import com.boss.cameraguard.chat.ChatRepository
import com.boss.cameraguard.chat.Conversation
import com.boss.cameraguard.data.CommunityRider
import com.boss.cameraguard.ui.theme.CameraGuardPalette
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Where the Chat tab currently is - hand-rolled state, matching the rest of the app's
 *  screen-state pattern (there is no navigation-compose dependency in this project). */
private sealed interface ChatRoute {
    data object List : ChatRoute
    data class Thread(val conversation: Conversation) : ChatRoute
    data object CreateGroup : ChatRoute
    data class GroupInfo(val conversation: Conversation) : ChatRoute
}

/**
 * Entry point for the Rider Community Chat tab. [pendingDirectTargetUid] lets another screen
 * (the Online Riders rider dialog, via "Message") jump straight into a direct conversation;
 * once consumed it calls [onPendingDirectTargetHandled] so the caller can clear it.
 */
@Composable
fun ChatHostScreen(
    modifier: Modifier = Modifier,
    myUid: String,
    myDisplayName: String,
    // Google-hosted account avatar URL only (never an upload) - see
    // CommunityRider.photoUrl for why this keeps rider photos free.
    myPhotoUrl: String? = null,
    communityRiders: List<CommunityRider>,
    pendingDirectTargetUid: String? = null,
    onPendingDirectTargetHandled: () -> Unit = {},
    // Tap target for a rider-message notification (a conversation id, direct or group -
    // see MainActivity.handleChatNotificationIntent / ChatNotifier.postChatNotification).
    pendingConversationId: String? = null,
    onPendingConversationIdHandled: () -> Unit = {}
) {
    var route by remember { mutableStateOf<ChatRoute>(ChatRoute.List) }
    var openError by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(myUid, myDisplayName, myPhotoUrl) {
        if (myDisplayName.isNotBlank() && myDisplayName != "Rider") {
            runCatching { ChatRepository.ensureChatProfile(myDisplayName, myPhotoUrl) }
        }
    }

    fun openDirect(uid: String) {
        opening = true
        openError = null
        scope.launch {
            try {
                val conversation = ChatRepository.getOrCreateDirectConversation(uid)
                route = ChatRoute.Thread(conversation)
            } catch (e: ChatBlockedException) {
                openError = e.message
            } catch (e: Exception) {
                openError = "Couldn't open that conversation. Check your connection and try again."
            } finally {
                opening = false
            }
        }
    }

    fun openById(conversationId: String) {
        if (conversationId.startsWith(DIRECT_SENTINEL)) {
            openDirect(conversationId.removePrefix(DIRECT_SENTINEL))
            return
        }
        opening = true
        openError = null
        scope.launch {
            try {
                val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("conversations").document(conversationId).get().await()
                val conversation = snap.toObject(Conversation::class.java)
                if (conversation != null) route = ChatRoute.Thread(conversation) else openError = "That conversation is no longer available."
            } catch (e: Exception) {
                openError = "Couldn't open that conversation. Check your connection and try again."
            } finally {
                opening = false
            }
        }
    }

    LaunchedEffect(pendingDirectTargetUid) {
        if (pendingDirectTargetUid != null) {
            openDirect(pendingDirectTargetUid)
            onPendingDirectTargetHandled()
        }
    }

    LaunchedEffect(pendingConversationId) {
        if (pendingConversationId != null) {
            openById(pendingConversationId)
            onPendingConversationIdHandled()
        }
    }

    Box(modifier.fillMaxSize()) {
        when (val current = route) {
            ChatRoute.List -> ConversationListScreen(
                myUid = myUid,
                communityRiders = communityRiders,
                onOpenConversation = ::openById,
                onCreateGroup = { route = ChatRoute.CreateGroup }
            )
            is ChatRoute.Thread -> ChatThreadScreen(
                myUid = myUid,
                myDisplayName = myDisplayName,
                conversation = current.conversation,
                onBack = { route = ChatRoute.List },
                onOpenGroupInfo = { route = ChatRoute.GroupInfo(current.conversation) }
            )
            ChatRoute.CreateGroup -> CreateGroupScreen(
                communityRiders = communityRiders,
                onBack = { route = ChatRoute.List },
                onGroupCreated = { conversationId -> openById(conversationId) }
            )
            is ChatRoute.GroupInfo -> GroupInfoScreen(
                myUid = myUid,
                conversation = current.conversation,
                communityRiders = communityRiders,
                onBack = { route = ChatRoute.Thread(current.conversation) },
                onLeft = { route = ChatRoute.List }
            )
        }

        if (opening) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CameraGuardPalette.Accent)
            }
        }

        openError?.let { message ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { openError = null },
                title = { androidx.compose.material3.Text("Couldn't open chat") },
                text = { androidx.compose.material3.Text(message) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { openError = null }) { androidx.compose.material3.Text("OK") }
                },
                containerColor = CameraGuardPalette.Surface,
                titleContentColor = CameraGuardPalette.Text,
                textContentColor = CameraGuardPalette.Muted
            )
        }
    }
}

private const val DIRECT_SENTINEL = "__direct__"
