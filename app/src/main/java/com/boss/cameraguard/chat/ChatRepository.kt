package com.boss.cameraguard.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Thrown when a Firestore write is rejected by security rules - most commonly because the
 *  recipient of a direct message has blocked the sender. Callers surface this distinctly
 *  from a network/offline failure. */
class ChatBlockedException(message: String) : Exception(message)

/**
 * Real (non-simulated) chat backend on Cloud Firestore + Cloud Storage. Every method here
 * talks to the live backend - there is no local/mock data path. See ChatModels.kt for the
 * schema and firebase-rules/firestore.rules + storage.rules for the server-side enforcement
 * this class relies on (only authorized members can read a conversation's messages/media;
 * a rider blocked by the recipient cannot write a new direct message - enforced by rules,
 * not just hidden in the UI).
 */
object ChatRepository {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private fun myUid(): String = FirebaseAuth.getInstance().currentUser?.uid
        ?: error("Chat requires a signed-in rider.")

    private fun conversations() = db.collection("conversations")
    private fun members(conversationId: String) = conversations().document(conversationId).collection("members")
    private fun messages(conversationId: String) = conversations().document(conversationId).collection("messages")
    private fun chatProfiles() = db.collection("chatProfiles")
    private fun blockedBy(uid: String) = db.collection("blocks").document(uid).collection("blocked")
    private fun reports() = db.collection("reports")

    // ---- Chat profile (public display identity) ---------------------------------------

    /** Keeps chatProfiles/{uid} in sync with the rider's current auth display name. Safe to
     *  call often (e.g. on app start and after a name change) - it's a merge write to a
     *  single small document the rider owns. */
    suspend fun ensureChatProfile(displayName: String, photoUrl: String? = null) {
        val uid = myUid()
        val data = mutableMapOf<String, Any>(
            "displayName" to displayName.ifBlank { "Rider" },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (photoUrl != null) data["photoUrl"] = photoUrl
        chatProfiles().document(uid).set(data, SetOptions.merge()).await()
    }

    suspend fun getChatProfile(uid: String): ChatProfile? =
        runCatching { chatProfiles().document(uid).get().await().toObject(ChatProfile::class.java) }.getOrNull()

    fun observeChatProfile(uid: String): Flow<ChatProfile?> = callbackFlow {
        val reg = chatProfiles().document(uid).addSnapshotListener { snap, _ ->
            trySend(snap?.toObject(ChatProfile::class.java))
        }
        awaitClose { reg.remove() }
    }

    // ---- Blocking ------------------------------------------------------------------------

    suspend fun blockUser(otherUid: String) {
        blockedBy(myUid()).document(otherUid).set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun unblockUser(otherUid: String) {
        blockedBy(myUid()).document(otherUid).delete().await()
    }

    /** Whether *I* have blocked otherUid (my own list - always readable to me). Whether
     *  otherUid has blocked me is intentionally NOT queryable (their list is private); a
     *  blocked send instead fails with [ChatBlockedException] when attempted - see sendText. */
    fun observeIHaveBlocked(otherUid: String): Flow<Boolean> = callbackFlow {
        val reg = blockedBy(myUid()).document(otherUid).addSnapshotListener { snap, _ ->
            trySend(snap?.exists() == true)
        }
        awaitClose { reg.remove() }
    }

    suspend fun reportUser(reportedUid: String, conversationId: String?, messageId: String?, reason: String) {
        reports().add(
            mapOf(
                "reporterUid" to myUid(),
                "reportedUid" to reportedUid,
                "conversationId" to conversationId,
                "messageId" to messageId,
                "reason" to reason.take(500),
                "status" to "open",
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    // ---- Conversations ---------------------------------------------------------------------

    fun observeMyConversations(): Flow<List<Conversation>> {
        val uid = myUid()
        return callbackFlow {
            val reg = conversations()
                .whereArrayContains("memberUids", uid)
                .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, err ->
                    if (err != null) return@addSnapshotListener
                    trySend(snap?.documents?.mapNotNull { it.toObject(Conversation::class.java) } ?: emptyList())
                }
            awaitClose { reg.remove() }
        }
    }

    /** Number of messages in the conversation sent after my last-read cursor. One-shot
     *  server-side aggregate count (Query.count()) - never downloads message bodies just to
     *  size a badge. Call again whenever the conversation's lastMessageAt changes. */
    suspend fun getUnreadCount(conversationId: String): Int {
        val uid = myUid()
        val lastReadAt = runCatching {
            members(conversationId).document(uid).get().await().getDate("lastReadAt")
        }.getOrNull()
        val base = messages(conversationId)
        val query = if (lastReadAt != null) base.whereGreaterThan("sentAt", lastReadAt) else base
        return runCatching {
            query.count().get(AggregateSource.SERVER).await().count.toInt()
        }.getOrDefault(0)
    }

    suspend fun markRead(conversationId: String) {
        members(conversationId).document(myUid())
            .set(mapOf("lastReadAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }

    /** Gets the existing direct conversation with otherUid, or creates it. Fails with
     *  [ChatBlockedException] if either rider has blocked the other (checked client-side
     *  first for a fast/clear error; the create/write is still rules-enforced server-side
     *  either way). */
    suspend fun getOrCreateDirectConversation(otherUid: String): Conversation {
        val uid = myUid()
        require(otherUid != uid) { "Can't start a conversation with yourself." }
        val iBlockedThem = blockedBy(uid).document(otherUid).get().await().exists()
        if (iBlockedThem) throw ChatBlockedException("You have blocked this rider. Unblock them first to send a message.")
        val id = directConversationId(uid, otherUid)
        val ref = conversations().document(id)
        // A missing document can be PERMISSION_DENIED under member-only Firestore
        // read rules: no resource exists yet on which membership can be checked.
        // A denied lookup is not proof that the recipient blocked us. The create
        // below remains protected by the server-side create and block rules.
        val existing = try {
            ref.get().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
        }
        if (existing?.exists() == true) return existing.toObject(Conversation::class.java)!!
        val data = mapOf(
            "type" to ConversationType.DIRECT.name,
            "memberUids" to listOf(uid, otherUid),
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastMessageAt" to FieldValue.serverTimestamp()
        )
        try {
            // Only my own membership doc is written here - Firestore rules let a rider write
            // only their own members/{uid} doc (see firestore.rules). The other rider's doc
            // is created lazily the first time THEY open the conversation (markRead(), which
            // upserts via SetOptions.merge()); until then conversation.memberUids (checked
            // by isMember()) already proves membership either way, so nothing is blocked.
            // Create the parent first. A batched child write cannot pass a rule
            // that reads the not-yet-existing parent with get(); after the parent
            // exists, the member document is optional and can be written normally.
            ref.set(data).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw ChatBlockedException("This rider isn't accepting messages from you right now.")
            }
            throw e
        }
        // Failure to record the read cursor must not hide a successfully created chat.
        runCatching {
            members(id).document(uid).set(
                mapOf("role" to "member", "joinedAt" to FieldValue.serverTimestamp())
            ).await()
        }
        return ref.get().await().toObject(Conversation::class.java)!!
    }

    suspend fun createGroup(name: String, memberUids: Set<String>): Conversation {
        val uid = myUid()
        val allMembers = (memberUids.filter { it.isNotBlank() } + uid).distinct()
        require(allMembers.size in 2..250) { "A group needs at least 2 members." }
        val ref = conversations().document()
        // Parent must exist before member-document rules can read its memberUids.
        ref.set(
            mapOf(
                "type" to ConversationType.GROUP.name,
                "memberUids" to allMembers,
                "groupName" to name.trim().ifBlank { "Rider group" }.take(60),
                "groupAdmins" to listOf(uid),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "lastMessageAt" to FieldValue.serverTimestamp()
            )
        ).await()
        runCatching {
            members(ref.id).document(uid).set(
                mapOf("role" to "admin", "joinedAt" to FieldValue.serverTimestamp())
            ).await()
        }
        val created = ref.get().await().toObject(Conversation::class.java)!!
        check(created.memberUids.containsAll(allMembers)) { "Group membership was not saved. Please try again." }
        return created
    }

    suspend fun renameGroup(conversationId: String, newName: String) {
        conversations().document(conversationId).update("groupName", newName.trim().take(60)).await()
    }

    /** Admin-only per firestore.rules. New members' own membership docs are created lazily
     *  the first time each of them opens the group (see markRead) - a rider can only ever
     *  write their own members/{uid} doc, so the admin adding them can't pre-create it. */
    suspend fun addGroupMembers(conversationId: String, uidsToAdd: Set<String>) {
        conversations().document(conversationId)
            .update("memberUids", FieldValue.arrayUnion(*uidsToAdd.toTypedArray())).await()
    }

    suspend fun leaveGroup(conversationId: String) {
        val uid = myUid()
        // One batch: security rules evaluate every operation in a batched write against the
        // database state from just before the batch, so both the conversation update and the
        // member-doc delete below still see me as a current member while they're checked -
        // done as two separate awaited calls, the second would already see me removed and
        // be rejected.
        db.batch()
            .update(conversations().document(conversationId), mapOf(
                "memberUids" to FieldValue.arrayRemove(uid),
                "groupAdmins" to FieldValue.arrayRemove(uid)
            ))
            .delete(members(conversationId).document(uid))
            .commit().await()
    }

    fun observeMembers(conversationId: String): Flow<List<ConversationMember>> = callbackFlow {
        val reg = members(conversationId).addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { it.toObject(ConversationMember::class.java) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    // ---- Messages --------------------------------------------------------------------------

    /** Always the newest [limit] messages, live. The thread screen loads older pages on
     *  demand with [loadOlderMessages] rather than ever subscribing to full history. */
    fun observeLatestMessages(conversationId: String, limit: Long = 40): Flow<List<ChatMessage>> = callbackFlow {
        val reg = messages(conversationId)
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                trySend(snap?.documents?.mapNotNull { it.toObject(ChatMessage::class.java) }?.reversed() ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun loadOlderMessages(conversationId: String, beforeOldest: ChatMessage, pageSize: Long = 40): List<ChatMessage> {
        val cutoff = beforeOldest.sentAt ?: return emptyList()
        val snap = messages(conversationId)
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .whereLessThan("sentAt", cutoff)
            .limit(pageSize)
            .get().await()
        return snap.documents.mapNotNull { it.toObject(ChatMessage::class.java) }.reversed()
    }

    private suspend fun touchConversationLastMessage(conversationId: String, text: String?, type: MessageType, senderUid: String) {
        conversations().document(conversationId).update(
            mapOf(
                "lastMessageText" to (text?.take(120) ?: when (type) {
                    MessageType.IMAGE -> "📷 Photo"
                    MessageType.VOICE -> "🎤 Voice note"
                    else -> ""
                }),
                "lastMessageType" to type.name,
                "lastMessageSenderUid" to senderUid,
                "lastMessageAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    private suspend fun runOrTranslateBlock(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw ChatBlockedException("This rider isn't accepting messages from you right now.")
            }
            throw e
        }
    }

    suspend fun sendText(conversationId: String, text: String, clientId: String = UUID.randomUUID().toString()) {
        val trimmed = text.trim().take(4000)
        require(trimmed.isNotEmpty()) { "Message is empty." }
        val uid = myUid()
        runOrTranslateBlock {
            messages(conversationId).document(clientId).set(
                mapOf(
                    "senderUid" to uid,
                    "type" to MessageType.TEXT.name,
                    "text" to trimmed,
                    "clientId" to clientId,
                    "sentAt" to FieldValue.serverTimestamp()
                )
            ).await()
            touchConversationLastMessage(conversationId, trimmed, MessageType.TEXT, uid)
            markRead(conversationId)
        }
    }

    /** Compresses to JPEG (longest edge capped, quality reduced until under [maxBytes]) and
     *  uploads to Storage before writing the message doc, so the message never references a
     *  half-uploaded file. */
    suspend fun sendImage(conversationId: String, uri: Uri, context: Context, maxLongEdge: Int = 1600, maxBytes: Int = 1_500_000, clientId: String = UUID.randomUUID().toString()) {
        val uid = myUid()
        val (bytes, width, height) = compressImage(context, uri, maxLongEdge, maxBytes)
        val path = "chatMedia/$conversationId/$clientId.jpg"
        val ref = storage.reference.child(path)
        ref.putBytes(bytes).await()
        val url = ref.downloadUrl.await().toString()
        runOrTranslateBlock {
            messages(conversationId).document(clientId).set(
                mapOf(
                    "senderUid" to uid,
                    "type" to MessageType.IMAGE.name,
                    "mediaPath" to path,
                    "mediaUrl" to url,
                    "mediaWidth" to width,
                    "mediaHeight" to height,
                    "clientId" to clientId,
                    "sentAt" to FieldValue.serverTimestamp()
                )
            ).await()
            touchConversationLastMessage(conversationId, null, MessageType.IMAGE, uid)
            markRead(conversationId)
        }
    }

    /** [localFile] is an AAC/M4A recording from VoiceRecorder; deleted by the caller after
     *  a successful send (see ChatThreadScreen) - the repository never leaves temp files
     *  behind itself. */
    suspend fun sendVoice(conversationId: String, localFile: File, durationMs: Long, clientId: String = UUID.randomUUID().toString(), maxBytes: Long = 4_000_000) {
        require(localFile.length() in 1..maxBytes) { "Voice note is too large to send." }
        val uid = myUid()
        val path = "chatMedia/$conversationId/$clientId.m4a"
        val ref = storage.reference.child(path)
        ref.putFile(Uri.fromFile(localFile)).await()
        val url = ref.downloadUrl.await().toString()
        runOrTranslateBlock {
            messages(conversationId).document(clientId).set(
                mapOf(
                    "senderUid" to uid,
                    "type" to MessageType.VOICE.name,
                    "mediaPath" to path,
                    "mediaUrl" to url,
                    "mediaDurationMs" to durationMs,
                    "clientId" to clientId,
                    "sentAt" to FieldValue.serverTimestamp()
                )
            ).await()
            touchConversationLastMessage(conversationId, null, MessageType.VOICE, uid)
            markRead(conversationId)
        }
    }

    private fun compressImage(context: Context, uri: Uri, maxLongEdge: Int, maxBytes: Int): Triple<ByteArray, Int, Int> {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longEdge / (sample * 2) >= maxLongEdge) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Could not read the selected image.")
        val scale = maxLongEdge.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else decoded
        var quality = 88
        var out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > maxBytes && quality > 35) {
            quality -= 12
            out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return Triple(out.toByteArray(), bitmap.width, bitmap.height)
    }
}
