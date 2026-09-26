package com.boss.cameraguard.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.boss.cameraguard.chat.VoicePlayer
import com.boss.cameraguard.ui.theme.CameraGuardPalette
import com.boss.cameraguard.ui.theme.neumorphicRaised
import com.boss.cameraguard.ui.theme.neumorphicInset
import kotlinx.coroutines.CoroutineScope

private val BubbleMine: Color get() = CameraGuardPalette.AccentContainer
private val BubbleTheirs: Color get() = CameraGuardPalette.Raised

@Composable
fun rememberVoicePlayer(scope: CoroutineScope): VoicePlayer {
    val player = remember { VoicePlayer(scope) }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    return player
}

@Composable
fun RiderAvatar(name: String, size: androidx.compose.ui.unit.Dp = 40.dp, photoUrl: String? = null) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
    Box(
        Modifier.size(size).clip(CircleShape).background(CameraGuardPalette.AccentContainer),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl != null) {
            AsyncImage(model = photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
        } else {
            Text(initials, color = CameraGuardPalette.Accent, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.6f).sp)
        }
    }
}

@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier.background(CameraGuardPalette.Danger, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TextMessageBubble(text: String, mine: Boolean, timeLabel: String, statusLabel: String?) {
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        val bubbleShape = RoundedCornerShape(
            topStart = 18.dp, topEnd = 18.dp,
            bottomStart = if (mine) 18.dp else 6.dp, bottomEnd = if (mine) 6.dp else 18.dp
        )
        Surface(
            color = Color.Transparent,
            shape = bubbleShape,
            modifier = Modifier.neumorphicRaised(bubbleShape, if (mine) BubbleMine else BubbleTheirs, 7.dp)
        ) {
            Text(text, color = CameraGuardPalette.Text, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp), fontSize = 15.sp)
        }
        MessageMeta(timeLabel, statusLabel)
    }
}

@Composable
fun ImageMessageBubble(url: String, mine: Boolean, timeLabel: String, statusLabel: String?, onOpenFullScreen: () -> Unit) {
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.widthIn(max = 230.dp).neumorphicRaised(RoundedCornerShape(22.dp), if (mine) BubbleMine else BubbleTheirs, 8.dp)
        ) {
            AsyncImage(
                model = url, contentDescription = "Shared photo",
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp).clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenFullScreen)
            )
        }
        MessageMeta(timeLabel, statusLabel)
    }
}

@Composable
fun VoiceMessageBubble(
    messageId: String, url: String, durationMs: Long, mine: Boolean, timeLabel: String, statusLabel: String?,
    player: VoicePlayer
) {
    val isThis = player.playingMessageId == messageId
    val failed = player.failedMessageId == messageId
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Surface(
            color = if (mine) BubbleMine else BubbleTheirs,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp
            )
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(min = 150.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { player.play(messageId, url) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (failed) Icons.Default.Refresh else if (isThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isThis) "Pause" else "Play", tint = CameraGuardPalette.Accent
                    )
                }
                LinearProgressIndicator(
                    progress = { if (isThis) player.progressFraction else 0f },
                    modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = CameraGuardPalette.Accent, trackColor = CameraGuardPalette.Border
                )
                Text(formatDurationShort(durationMs), color = CameraGuardPalette.Muted, fontSize = 11.sp)
            }
        }
        MessageMeta(timeLabel, statusLabel, extra = if (failed) "Playback failed - tap to retry" else null)
    }
}

@Composable
private fun MessageMeta(timeLabel: String, statusLabel: String?, extra: String? = null) {
    Row(Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(timeLabel, color = CameraGuardPalette.Muted, fontSize = 10.sp)
        statusLabel?.let { Text(it, color = CameraGuardPalette.Muted, fontSize = 10.sp) }
        extra?.let { Text(it, color = CameraGuardPalette.Danger, fontSize = 10.sp) }
    }
}

fun formatDurationShort(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

/** A small fixed row of common emoji, tapped to insert into the composer - a lightweight
 *  supplement to the Android keyboard's own emoji picker (which every message field already
 *  supports natively), not a replacement for it. */
@Composable
fun QuickEmojiRow(onPick: (String) -> Unit) {
    val emoji = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🏍️", "⚠️")
    LazyRowEmoji(emoji, onPick)
}

@Composable
private fun LazyRowEmoji(emoji: List<String>, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(emoji, key = { it }) { e ->
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { onPick(e) }.padding(6.dp),
                contentAlignment = Alignment.Center
            ) { Text(e, fontSize = 20.sp) }
        }
    }
}

@Composable
fun BlockedBanner(displayName: String) {
    Surface(color = CameraGuardPalette.Danger.copy(alpha = .12f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CameraGuardPalette.Danger)) {
        Text(
            "You've blocked $displayName. Unblock them from the menu above to send messages again.",
            color = CameraGuardPalette.Danger, fontSize = 12.sp, modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
fun CannotSendBanner(reason: String) {
    Surface(color = CameraGuardPalette.Warning.copy(alpha = .14f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CameraGuardPalette.Warning)) {
        Text(reason, color = CameraGuardPalette.Warning, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
    }
}
