package com.boss.cameraguard.community

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

/** Lightweight rider-community feed. Location is intentionally never attached to posts. */
data class CommunityPost(
    val id: String = "",
    val authorUid: String = "",
    val authorName: String = "Rider",
    val text: String = "",
    val createdAt: Timestamp? = null
)

object CommunityFeedRepository {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    fun isGoogleLinked(): Boolean = auth.currentUser?.let { user ->
        !user.isAnonymous && user.providerData.any { it.providerId == "google.com" }
    } == true

    fun ensureMemberProfile(displayName: String, onComplete: (Result<Unit>) -> Unit) {
        val user = auth.currentUser ?: return onComplete(Result.failure(IllegalStateException("No signed-in user")))
        if (!isGoogleLinked()) return onComplete(Result.failure(IllegalStateException("Google account required")))
        val cleanName = displayName.trim().take(40).ifBlank { user.displayName?.trim()?.take(40).orEmpty() }.ifBlank { "Rider" }
        val payload = mapOf(
            "displayName" to cleanName,
            "joinedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "provider" to "GOOGLE",
            "active" to true
        )
        db.collection("communityMembers").document(user.uid)
            .set(payload, SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun observePosts(onChange: (List<CommunityPost>) -> Unit, onError: (Throwable) -> Unit): ListenerRegistration =
        db.collection("communityPosts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(80)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error); return@addSnapshotListener }
                val posts = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    doc.toObject(CommunityPost::class.java)?.copy(id = doc.id)
                }
                onChange(posts)
            }

    fun publishTextPost(displayName: String, text: String, onComplete: (Result<Unit>) -> Unit) {
        val user = auth.currentUser ?: return onComplete(Result.failure(IllegalStateException("No signed-in user")))
        if (!isGoogleLinked()) return onComplete(Result.failure(IllegalStateException("Google account required")))
        val clean = text.trim()
        if (clean.isEmpty()) return onComplete(Result.failure(IllegalArgumentException("Post is empty")))
        if (clean.length > 1200) return onComplete(Result.failure(IllegalArgumentException("Post is too long")))
        val name = displayName.trim().take(40).ifBlank { user.displayName?.trim()?.take(40).orEmpty() }.ifBlank { "Rider" }
        val ref = db.collection("communityPosts").document()
        val payload = mapOf(
            "authorUid" to user.uid,
            "authorName" to name,
            "text" to clean,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        ref.set(payload)
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }
}
