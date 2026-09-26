package com.boss.cameraguard.chat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.boss.cameraguard.MainActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Tracks which chat screen is currently visible so [ChatNewMessageWatcher] doesn't notify
 * about a conversation the rider is already looking at. Deliberately a plain in-memory
 * holder (not persisted) - it only needs to reflect "right now, in this process".
 */
object ChatUiState {
    val openConversationId = mutableStateOf<String?>(null)
}

private const val CHANNEL_ID = "cameraguard_chat"

fun ensureChatNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
        CHANNEL_ID, "Rider messages", NotificationManager.IMPORTANCE_HIGH
    ).apply { description = "New private and group messages from other riders" }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/**
 * Local, in-app notification only - there is no push-notification backend in this build (no
 * Cloud Function/FCM wiring - see the change report for why, and how to add it later). This
 * fires only while CameraGuard's process is alive (foreground or recently backgrounded); it
 * will NOT arrive after the app is fully killed or the device reboots.
 */
fun postChatNotification(context: Context, conversationId: String, title: String, body: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    // Brings CameraGuard to the foreground; it does not deep-link straight into this
    // conversation's thread (that would need MainActivity's onNewIntent/launch handling
    // reworked, which this change intentionally leaves untouched). The rider lands wherever
    // they left the app and taps the Chat tab, whose unread badge points them to it.
    val openIntent = android.content.Intent(context, MainActivity::class.java).apply {
        flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context, conversationId.hashCode(), openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    // Message text itself is kept out of the notification body to avoid surfacing private
    // conversation contents where the OS or another app could read it off the lock screen;
    // only the sender/group name and a generic "sent a message" line are shown.
    // No app icon resources exist in this project (DrivingService's own notification uses a
    // system drawable the same way - see createDrivingNotification()); matching that
    // convention here rather than introducing a new drawable resource for this change.
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    runCatching {
        androidx.core.app.NotificationManagerCompat.from(context).notify(conversationId.hashCode(), notification)
    }
}

/**
 * Watches the rider's conversation list for the lifetime of the composable it's placed in
 * and posts a local notification when another rider's message arrives in a conversation
 * that isn't the one currently open. Call once, near the app root (see CameraGuardApp).
 */
@Composable
fun ChatNewMessageWatcher() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        ensureChatNotificationChannel(context)
        val seenLastMessageAt = HashMap<String, Long>()
        var firstSnapshot = true
        ChatRepository.observeMyConversations().collect { conversations ->
            val myUid = FirebaseAuth.getInstance().currentUser?.uid
            for (conversation in conversations) {
                val at = conversation.lastMessageAt?.time ?: continue
                val previous = seenLastMessageAt[conversation.id]
                seenLastMessageAt[conversation.id] = at
                val isNew = previous != null && at > previous
                val fromSomeoneElse = conversation.lastMessageSenderUid != null && conversation.lastMessageSenderUid != myUid
                val notOpen = ChatUiState.openConversationId.value != conversation.id
                if (!firstSnapshot && isNew && fromSomeoneElse && notOpen) {
                    val senderName = ChatRepository.getChatProfile(conversation.lastMessageSenderUid!!)?.displayName ?: "A rider"
                    val title = conversation.groupName ?: senderName
                    val body = if (conversation.groupName != null) "$senderName: ${conversation.lastMessageText.orEmpty()}"
                        else conversation.lastMessageText ?: "sent a message"
                    postChatNotification(context, conversation.id, title, body)
                }
            }
            firstSnapshot = false
        }
    }
}
