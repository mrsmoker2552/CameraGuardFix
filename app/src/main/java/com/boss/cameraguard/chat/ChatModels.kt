package com.boss.cameraguard.chat

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Rider Community Chat data model.
 *
 * Backend: Cloud Firestore + Cloud Storage (see ChatRepository.kt for why this feature uses
 * Firestore/Storage rather than the app's existing Realtime Database, which stays untouched
 * for presence/cameras/road-reports/admins).
 *
 * Collections:
 *  - chatProfiles/{uid}                         public-readable minimal chat identity
 *  - blocks/{uid}/blocked/{blockedUid}           private block list, owner-only
 *  - conversations/{conversationId}              conversation metadata (direct or group)
 *  - conversations/{conversationId}/members/{uid} per-member join info + read cursor
 *  - conversations/{conversationId}/messages/{messageId} message documents
 *  - reports/{reportId}                          user/message reports, write-only from the app
 *
 * Direct-conversation IDs are deterministic: "dm_<lowUid>_<highUid>" (sorted so both riders
 * resolve to the same conversation and duplicates can't be created). Group IDs are Firestore
 * auto-IDs.
 */

/** Public, minimal identity used to render a rider's name/avatar in chat. Never contains
 *  location, email, or phone number - see chatProfiles rule in firestore.rules. */
data class ChatProfile(
    @DocumentId val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    @ServerTimestamp val updatedAt: Date? = null
)

enum class ConversationType { DIRECT, GROUP }

enum class MessageType { TEXT, IMAGE, VOICE, SYSTEM }

data class Conversation(
    @DocumentId val id: String = "",
    @get:PropertyName("type") @set:PropertyName("type")
    var typeRaw: String = ConversationType.DIRECT.name,
    val memberUids: List<String> = emptyList(),
    val groupName: String? = null,
    val groupAdmins: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    val lastMessageText: String? = null,
    val lastMessageType: String? = null,
    val lastMessageSenderUid: String? = null,
    @ServerTimestamp val lastMessageAt: Date? = null
) {
    @get:Exclude
    val type: ConversationType
        get() = runCatching { ConversationType.valueOf(typeRaw) }.getOrDefault(ConversationType.DIRECT)

    @Exclude
    fun otherUid(myUid: String): String? =
        if (type == ConversationType.DIRECT) memberUids.firstOrNull { it != myUid } else null
}

data class ConversationMember(
    @DocumentId val uid: String = "",
    val role: String = "member", // "admin" | "member"
    @ServerTimestamp val joinedAt: Date? = null,
    @ServerTimestamp val lastReadAt: Date? = null
)

data class ChatMessage(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    @get:PropertyName("type") @set:PropertyName("type")
    var typeRaw: String = MessageType.TEXT.name,
    val text: String? = null,
    val mediaPath: String? = null,
    val mediaUrl: String? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    val mediaDurationMs: Long? = null,
    val clientId: String = "",
    @ServerTimestamp val sentAt: Date? = null
) {
    @get:Exclude
    val type: MessageType
        get() = runCatching { MessageType.valueOf(typeRaw) }.getOrDefault(MessageType.TEXT)
}

/** Client-side send state for optimistic UI - not persisted. */
enum class SendState { SENDING, SENT, FAILED }

data class OutgoingMessage(
    val clientId: String,
    val message: ChatMessage,
    val state: SendState,
    val localImageUri: String? = null,
    val localVoicePath: String? = null
)

data class ConversationSummary(
    val conversation: Conversation,
    val otherProfile: ChatProfile?,
    val unreadCount: Int
)

/** conversation id helper, kept next to the model it names. */
fun directConversationId(uidA: String, uidB: String): String {
    val (low, high) = if (uidA <= uidB) uidA to uidB else uidB to uidA
    return "dm_${low}_$high"
}
