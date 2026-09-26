package com.boss.cameraguard.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boss.cameraguard.chat.ChatProfile
import com.boss.cameraguard.chat.ChatRepository
import com.boss.cameraguard.chat.Conversation
import com.boss.cameraguard.chat.ConversationType
import com.boss.cameraguard.data.CommunityRider
import com.boss.cameraguard.ui.theme.CameraGuardPalette
import com.boss.cameraguard.ui.theme.neumorphicRaised
import com.boss.cameraguard.ui.theme.neumorphicInset
import java.text.DateFormat
import java.util.Date

private enum class ConversationFilter { ALL, UNREAD, GROUPS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    modifier: Modifier = Modifier,
    myUid: String,
    communityRiders: List<CommunityRider>,
    onOpenConversation: (String) -> Unit,
    onCreateGroup: () -> Unit
) {
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val profileCache = remember { mutableStateMapOf<String, ChatProfile?>() }
    val unreadCache = remember { mutableStateMapOf<String, Int>() }
    var showPicker by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(ConversationFilter.ALL) }

    LaunchedEffect(Unit) {
        ChatRepository.observeMyConversations().collect {
            conversations = it
            loaded = true
        }
    }

    LaunchedEffect(conversations) {
        conversations.forEach { conversation ->
            val other = conversation.otherUid(myUid)
            if (other != null && other !in profileCache) profileCache[other] = ChatRepository.getChatProfile(other)
            unreadCache[conversation.id] = ChatRepository.getUnreadCount(conversation.id)
        }
    }

    val unreadTotal = unreadCache.values.sum()
    val visibleConversations = remember(conversations, unreadCache.toMap(), filter) {
        when (filter) {
            ConversationFilter.ALL -> conversations
            ConversationFilter.UNREAD -> conversations.filter { (unreadCache[it.id] ?: 0) > 0 }
            ConversationFilter.GROUPS -> conversations.filter { it.type == ConversationType.GROUP }
        }
    }

    Box(modifier.fillMaxSize().background(CameraGuardPalette.Background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Messages", color = CameraGuardPalette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (unreadTotal > 0) "$unreadTotal unread across your rider conversations" else "Private and group rider conversations",
                        color = CameraGuardPalette.Muted,
                        fontSize = 12.sp
                    )
                }
                FilledIconButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.shadow(9.dp, RoundedCornerShape(16.dp), clip = false),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CameraGuardPalette.Accent,
                        contentColor = CameraGuardPalette.Background
                    )
                ) { Icon(Icons.Default.Add, contentDescription = "New conversation") }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConversationFilter.entries.forEach { item ->
                    val selected = filter == item
                    val label = when (item) {
                        ConversationFilter.ALL -> "All"
                        ConversationFilter.UNREAD -> if (unreadTotal > 0) "Unread $unreadTotal" else "Unread"
                        ConversationFilter.GROUPS -> "Groups"
                    }
                    Surface(
                        color = if (selected) CameraGuardPalette.AccentContainer else CameraGuardPalette.Surface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (selected) CameraGuardPalette.Accent.copy(alpha=.65f) else CameraGuardPalette.Border.copy(alpha=.4f)),
                        modifier = Modifier.neumorphicRaised(RoundedCornerShape(18.dp), if (selected) CameraGuardPalette.AccentContainer else CameraGuardPalette.Surface, if (selected) 8.dp else 5.dp).clickable { filter = item }
                    ) {
                        Text(
                            label,
                            color = if (selected) CameraGuardPalette.Accent else CameraGuardPalette.Muted,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            if (loaded && visibleConversations.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Groups, null, tint = CameraGuardPalette.Muted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when (filter) {
                            ConversationFilter.ALL -> "No conversations yet"
                            ConversationFilter.UNREAD -> "You're all caught up"
                            ConversationFilter.GROUPS -> "No groups yet"
                        },
                        color = CameraGuardPalette.Text,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when (filter) {
                            ConversationFilter.ALL -> "Tap + to message an online rider or create a group."
                            ConversationFilter.UNREAD -> "New rider messages will appear here."
                            ConversationFilter.GROUPS -> "Create a rider group from the + button."
                        },
                        color = CameraGuardPalette.Muted,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleConversations, key = { it.id }) { conversation ->
                        val other = conversation.otherUid(myUid)
                        val liveRider = communityRiders.firstOrNull { it.uid == other }
                        val liveName = liveRider?.displayName
                        val savedName = profileCache[other]?.displayName
                        val title = if (conversation.type == ConversationType.GROUP) {
                            conversation.groupName?.takeIf { it.isNotBlank() } ?: "Rider group"
                        } else {
                            liveName?.takeIf { it.isNotBlank() && it != "Rider" }
                                ?: savedName?.takeIf { it.isNotBlank() && it != "Rider" }
                                ?: liveName?.takeIf { it.isNotBlank() }
                                ?: savedName?.takeIf { it.isNotBlank() }
                                ?: "Rider"
                        }
                        ConversationRow(
                            title = title,
                            isGroup = conversation.type == ConversationType.GROUP,
                            online = liveRider != null,
                            preview = conversation.lastMessageText ?: "No messages yet",
                            timestamp = conversation.lastMessageAt,
                            unread = unreadCache[conversation.id] ?: 0,
                            onClick = { onOpenConversation(conversation.id) }
                        )
                    }
                }
            }
        }

        if (showPicker) {
            StartConversationSheet(
                communityRiders = communityRiders,
                onDismiss = { showPicker = false },
                onPickRider = { uid -> showPicker = false; onOpenConversation("__direct__$uid") },
                onCreateGroup = { showPicker = false; onCreateGroup() }
            )
        }
    }
}

@Composable
private fun ConversationRow(
    title: String,
    isGroup: Boolean,
    online: Boolean,
    preview: String,
    timestamp: Date?,
    unread: Int,
    onClick: () -> Unit
) {
    Surface(
        color = if (unread > 0) CameraGuardPalette.Raised else CameraGuardPalette.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (unread > 0) CameraGuardPalette.Accent.copy(alpha = .42f) else CameraGuardPalette.Border.copy(alpha = .65f)),
        modifier = Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(20.dp), if (unread > 0) CameraGuardPalette.Raised else CameraGuardPalette.Surface, if (unread > 0) 10.dp else 7.dp).clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                if (isGroup) {
                    Box(Modifier.size(46.dp).background(CameraGuardPalette.AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, null, tint = CameraGuardPalette.Accent)
                    }
                } else RiderAvatar(title, size = 46.dp)
                if (!isGroup && online) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(13.dp)
                            .background(CameraGuardPalette.Surface, CircleShape).padding(2.dp)
                    ) { Box(Modifier.fillMaxSize().background(CameraGuardPalette.Success, CircleShape)) }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = CameraGuardPalette.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    preview,
                    color = if (unread > 0) CameraGuardPalette.Text else CameraGuardPalette.Muted,
                    fontSize = 13.sp,
                    fontWeight = if (unread > 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                timestamp?.let { Text(shortTimeLabel(it), color = CameraGuardPalette.Muted, fontSize = 10.sp) }
                Spacer(Modifier.height(5.dp))
                UnreadBadge(unread)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartConversationSheet(
    communityRiders: List<CommunityRider>,
    onDismiss: () -> Unit,
    onPickRider: (String) -> Unit,
    onCreateGroup: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CameraGuardPalette.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Start a conversation", color = CameraGuardPalette.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Message a rider currently visible online, or create a private rider group.",
                color = CameraGuardPalette.Muted,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                color = CameraGuardPalette.Raised,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CameraGuardPalette.Border),
                modifier = Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(16.dp), CameraGuardPalette.Raised, 7.dp).clickable(onClick = onCreateGroup)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Groups, null, tint = CameraGuardPalette.Highlight)
                    Text("Create a group", color = CameraGuardPalette.Text, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (communityRiders.isEmpty()) {
                Text("No riders are online nearby right now.", color = CameraGuardPalette.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                communityRiders.sortedBy { it.displayName }.forEach { rider ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPickRider(rider.uid) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box {
                            RiderAvatar(rider.displayName, size = 38.dp)
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(11.dp)
                                    .background(CameraGuardPalette.Surface, CircleShape).padding(2.dp)
                            ) { Box(Modifier.fillMaxSize().background(CameraGuardPalette.Success, CircleShape)) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(rider.displayName.ifBlank { "Rider" }, color = CameraGuardPalette.Text, fontWeight = FontWeight.SemiBold)
                            Text(rider.roadName ?: "Online nearby", color = CameraGuardPalette.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun shortTimeLabel(date: Date): String {
    val diff = System.currentTimeMillis() - date.time
    return if (diff < 24 * 3600_000L) DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    else DateFormat.getDateInstance(DateFormat.SHORT).format(date)
}
