package com.boss.cameraguard.ui.community

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boss.cameraguard.CameraGuardAuthManager
import com.boss.cameraguard.community.CommunityFeedRepository
import com.boss.cameraguard.community.CommunityPost
import com.boss.cameraguard.data.CommunityRider
import com.boss.cameraguard.ui.chat.ChatHostScreen
import com.boss.cameraguard.ui.chat.RiderAvatar
import com.boss.cameraguard.ui.theme.CameraGuardPalette
import com.boss.cameraguard.ui.theme.neumorphicRaised
import com.boss.cameraguard.ui.theme.neumorphicInset
import kotlinx.coroutines.launch
import java.text.DateFormat

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return context as? Activity
}

private enum class CommunitySection { FEED, RIDERS, MESSAGES }

@Composable
fun CommunityHostScreen(
    modifier: Modifier = Modifier,
    myUid: String?,
    myDisplayName: String,
    communityEnabled: Boolean,
    communityRiders: List<CommunityRider>,
    pendingDirectTargetUid: String? = null,
    onPendingDirectTargetHandled: () -> Unit = {},
    onCommunityJoined: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var authUser by remember { mutableStateOf(CameraGuardAuthManager.currentUser()) }
    var joinBusy by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableStateOf(CommunitySection.FEED) }

    LaunchedEffect(pendingDirectTargetUid) {
        if (pendingDirectTargetUid != null) section = CommunitySection.MESSAGES
    }

    DisposableEffect(Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { authUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val googleLinked = authUser?.let { u -> !u.isAnonymous && u.providerData.any { it.providerId == "google.com" } } == true
    val effectiveName = myDisplayName.takeIf { it.isNotBlank() && it != "Rider" }
        ?: authUser?.displayName?.takeIf { it.isNotBlank() }
        ?: "Rider"
    val joined = googleLinked && communityEnabled

    if (!joined) {
        CommunityJoinScreen(
            modifier = modifier,
            googleLinked = googleLinked,
            busy = joinBusy,
            error = joinError,
            onJoin = {
                if (activity == null) {
                    joinError = "Google sign-in is unavailable on this screen."
                    return@CommunityJoinScreen
                }
                joinBusy = true
                joinError = null
                scope.launch {
                    try {
                        if (!CommunityFeedRepository.isGoogleLinked()) CameraGuardAuthManager.signInWithGoogle(activity)
                        val refreshed = CameraGuardAuthManager.refreshUser()
                        val name = refreshed.displayName?.trim()?.take(40).orEmpty().ifBlank { effectiveName }
                        CommunityFeedRepository.ensureMemberProfile(name) { result ->
                            result.onSuccess { onCommunityJoined(name) }
                                .onFailure { joinError = it.message ?: "Couldn't join Community." }
                            joinBusy = false
                        }
                    } catch (e: Exception) {
                        joinError = when (e) {
                            is com.boss.cameraguard.GoogleSignInUiException ->
                                if (e.cancelled) "Sign-in was cancelled. Tap Continue with Google to try again."
                                else e.message ?: "Google sign-in failed. Please try again."
                            else -> e.message ?: "Google sign-in failed. Please try again."
                        }
                        joinBusy = false
                    }
                }
            }
        )
        return
    }

    Column(modifier.fillMaxSize().background(CameraGuardPalette.Background)) {
        CommunityHeader(effectiveName = effectiveName, onlineCount = communityRiders.size)
        CommunitySectionPicker(section = section, onSelect = { section = it })
        Spacer(Modifier.height(6.dp))
        when (section) {
            CommunitySection.FEED -> CommunityFeedScreen(Modifier.weight(1f), effectiveName)
            CommunitySection.RIDERS -> CommunityRidersScreen(Modifier.weight(1f), communityRiders)
            CommunitySection.MESSAGES -> if (myUid != null) {
                ChatHostScreen(
                    modifier = Modifier.weight(1f),
                    myUid = myUid,
                    myDisplayName = effectiveName,
                    communityRiders = communityRiders,
                    pendingDirectTargetUid = pendingDirectTargetUid,
                    onPendingDirectTargetHandled = onPendingDirectTargetHandled
                )
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CameraGuardPalette.Accent)
                }
            }
        }
    }
}

@Composable
private fun CommunityHeader(effectiveName: String, onlineCount: Int) {
    Surface(
        color = CameraGuardPalette.Surface,
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        border = BorderStroke(1.dp, CameraGuardPalette.Border.copy(alpha = .35f)),
        modifier = Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp), CameraGuardPalette.Surface, 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                RiderAvatar(effectiveName, size = 48.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(13.dp)
                        .background(CameraGuardPalette.Surface, CircleShape).padding(2.dp)
                ) { Box(Modifier.fillMaxSize().background(CameraGuardPalette.Success, CircleShape)) }
            }
            Column(Modifier.weight(1f)) {
                Text("Community", color = CameraGuardPalette.Text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("$effectiveName · $onlineCount riders online nearby", color = CameraGuardPalette.Muted, fontSize = 11.sp)
            }
            Surface(color = CameraGuardPalette.AccentContainer, shape = RoundedCornerShape(999.dp)) {
                Text("MEMBER", color = CameraGuardPalette.Accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun CommunitySectionPicker(section: CommunitySection, onSelect: (CommunitySection) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommunitySection.entries.forEach { item ->
            val selected = section == item
            val (label, icon) = when (item) {
                CommunitySection.FEED -> "Feed" to Icons.Default.List
                CommunitySection.RIDERS -> "Riders" to Icons.Default.Groups
                CommunitySection.MESSAGES -> "Messages" to Icons.Default.Chat
            }
            Surface(
                color = if (selected) CameraGuardPalette.AccentContainer else CameraGuardPalette.Surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (selected) CameraGuardPalette.Accent else CameraGuardPalette.Border),
                modifier = Modifier.weight(1f).neumorphicRaised(RoundedCornerShape(18.dp), if (selected) CameraGuardPalette.AccentContainer else CameraGuardPalette.Surface, if (selected) 9.dp else 6.dp).clickable { onSelect(item) }
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = if (selected) CameraGuardPalette.Accent else CameraGuardPalette.Muted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = if (selected) CameraGuardPalette.Accent else CameraGuardPalette.Muted,
                        fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun CommunityJoinScreen(modifier: Modifier, googleLinked: Boolean, busy: Boolean, error: String?, onJoin: () -> Unit) {
    Box(modifier.fillMaxSize().background(CameraGuardPalette.Background), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.padding(22.dp).fillMaxWidth().neumorphicRaised(RoundedCornerShape(28.dp), CameraGuardPalette.Surface, 14.dp),
            colors = CardDefaults.cardColors(containerColor = CameraGuardPalette.Surface),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, CameraGuardPalette.Border)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(70.dp).background(CameraGuardPalette.AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Groups, null, tint = CameraGuardPalette.Accent, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Join Smoker's Rider Community", color = CameraGuardPalette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "A rider-only space for posts, nearby riders and private/group messages.",
                    color = CameraGuardPalette.Muted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Surface(color = CameraGuardPalette.Raised, shape = RoundedCornerShape(14.dp)) {
                    Text(
                        "Google sign-in is required only for Community. Posts never attach your precise location automatically; location sharing stays explicit, such as SOS.",
                        color = CameraGuardPalette.Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = CameraGuardPalette.Danger, fontSize = 12.sp) }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onJoin,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp).shadow(10.dp, RoundedCornerShape(20.dp), clip = false),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CameraGuardPalette.Accent, contentColor = CameraGuardPalette.Background)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text(if (googleLinked) "Join Community" else "Continue with Google")
                }
            }
        }
    }
}

@Composable
private fun CommunityFeedScreen(modifier: Modifier, displayName: String) {
    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val registration = CommunityFeedRepository.observePosts({ posts = it; error = null }, { error = it.message })
        onDispose { registration.remove() }
    }

    Column(modifier.padding(horizontal = 12.dp)) {
        Surface(
            color = CameraGuardPalette.Surface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, CameraGuardPalette.Border.copy(alpha = .45f)),
            modifier = Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(24.dp), CameraGuardPalette.Surface, 10.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RiderAvatar(displayName, size = 38.dp)
                    Column {
                        Text("Share with riders", color = CameraGuardPalette.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("No automatic location sharing", color = CameraGuardPalette.Muted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 1200) text = it },
                    modifier = Modifier.fillMaxWidth().neumorphicInset(RoundedCornerShape(18.dp), CameraGuardPalette.Raised),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CameraGuardPalette.Accent.copy(alpha=.55f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    placeholder = { Text("Road update, tip, question or rider post…") },
                    minLines = 2,
                    maxLines = 5,
                    trailingIcon = {
                        IconButton(enabled = text.isNotBlank() && !sending, onClick = {
                            sending = true
                            CommunityFeedRepository.publishTextPost(displayName, text) { result ->
                                result.onSuccess { text = ""; error = null }.onFailure { error = it.message }
                                sending = false
                            }
                        }) { Icon(Icons.Default.Send, "Post", tint = CameraGuardPalette.Accent) }
                    }
                )
                Text("${text.length}/1200", color = CameraGuardPalette.Muted, fontSize = 9.sp, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
            }
        }
        error?.let { Text(it, color = CameraGuardPalette.Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(10.dp))
        if (posts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No posts yet. Be the first rider to post.", color = CameraGuardPalette.Muted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
                items(posts, key = { it.id }) { post ->
                    Card(
                        modifier = Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(22.dp), CameraGuardPalette.Surface, 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, CameraGuardPalette.Border.copy(alpha = .7f))
                    ) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RiderAvatar(post.authorName.ifBlank { "Rider" }, size = 38.dp)
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(post.authorName.ifBlank { "Rider" }, color = CameraGuardPalette.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    post.createdAt?.toDate()?.let {
                                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it), color = CameraGuardPalette.Muted, fontSize = 9.sp)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(post.text, color = CameraGuardPalette.Text, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityRidersScreen(modifier: Modifier, riders: List<CommunityRider>) {
    if (riders.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No nearby riders online.", color = CameraGuardPalette.Muted)
        }
        return
    }
    LazyColumn(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
        items(riders, key = { it.uid }) { rider ->
            Card(
                modifier = Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(22.dp), CameraGuardPalette.Surface, 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, if (rider.sosActive) CameraGuardPalette.Danger else CameraGuardPalette.Border.copy(alpha = .7f))
            ) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Box {
                        RiderAvatar(rider.displayName.ifBlank { "Rider" }, size = 44.dp)
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(12.dp)
                                .background(CameraGuardPalette.Surface, CircleShape).padding(2.dp)
                        ) { Box(Modifier.fillMaxSize().background(if (rider.sosActive) CameraGuardPalette.Danger else CameraGuardPalette.Success, CircleShape)) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(rider.displayName.ifBlank { "Rider" }, color = CameraGuardPalette.Text, fontWeight = FontWeight.Bold)
                        Text(rider.roadName ?: "Road unavailable", color = CameraGuardPalette.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Surface(
                        color = if (rider.sosActive) CameraGuardPalette.Danger.copy(alpha = .14f) else CameraGuardPalette.AccentContainer,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            if (rider.sosActive) "SOS" else if (rider.moving) "${rider.speedKmh.toInt()} km/h" else "Online",
                            color = if (rider.sosActive) CameraGuardPalette.Danger else CameraGuardPalette.Accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}
