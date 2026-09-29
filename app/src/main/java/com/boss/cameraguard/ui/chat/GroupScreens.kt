package com.boss.cameraguard.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boss.cameraguard.chat.ChatProfile
import com.boss.cameraguard.chat.ChatRepository
import com.boss.cameraguard.chat.Conversation
import com.boss.cameraguard.data.CommunityRider
import com.boss.cameraguard.ui.theme.CameraGuardPalette
import com.boss.cameraguard.ui.theme.neumorphicRaised
import com.boss.cameraguard.ui.theme.neumorphicInset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class) // TopAppBar is still experimental in Material3
@Composable
fun CreateGroupScreen(
    modifier: Modifier = Modifier,
    communityRiders: List<CommunityRider>,
    onBack: () -> Unit,
    onGroupCreated: (String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selected = remember { mutableStateSetOf<String>() }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = CameraGuardPalette.Background,
        topBar = {
            TopAppBar(
                title = { Text("New group", color = CameraGuardPalette.Text) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CameraGuardPalette.Text) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CameraGuardPalette.Surface)
            )
        }
    ) { padding ->
        Column(modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = groupName, onValueChange = { groupName = it },
                label = { Text("Group name") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text("Add riders currently online nearby", color = CameraGuardPalette.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            if (communityRiders.isEmpty()) {
                Text("No riders online nearby right now.", color = CameraGuardPalette.Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(communityRiders.sortedBy { it.displayName }, key = { it.uid }) { rider ->
                        val isSelected = rider.uid in selected
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (isSelected) selected.remove(rider.uid) else selected.add(rider.uid) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RiderAvatar(rider.displayName, size = 36.dp, photoUrl = rider.photoUrl)
                            Text(rider.displayName.ifBlank { "Rider" }, color = CameraGuardPalette.Text, modifier = Modifier.weight(1f))
                            Checkbox(checked = isSelected, onCheckedChange = { checked -> if (checked) selected.add(rider.uid) else selected.remove(rider.uid) })
                        }
                    }
                }
            }
            error?.let { Text(it, color = CameraGuardPalette.Danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            Button(
                onClick = {
                    if (groupName.isBlank()) { error = "Give the group a name."; return@Button }
                    if (selected.size < 1) { error = "Add at least one other rider."; return@Button }
                    creating = true
                    error = null
                    scope.launch {
                        try {
                            val conversation = ChatRepository.createGroup(groupName, selected.toSet())
                            onGroupCreated(conversation.id)
                        } catch (e: Exception) {
                            error = e.message ?: "Couldn't create the group. Try again."
                        } finally {
                            creating = false
                        }
                    }
                },
                enabled = !creating,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CameraGuardPalette.Accent, contentColor = CameraGuardPalette.OnAccent)
            ) {
                if (creating) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CameraGuardPalette.Background)
                else Text("Create group (${selected.size + 1} members)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // TopAppBar is still experimental in Material3
@Composable
fun GroupInfoScreen(
    modifier: Modifier = Modifier,
    myUid: String,
    conversation: Conversation,
    communityRiders: List<CommunityRider>,
    onBack: () -> Unit,
    onLeft: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var memberProfiles by remember { mutableStateOf<Map<String, ChatProfile?>>(emptyMap()) }
    var groupName by remember(conversation.id) { mutableStateOf(conversation.groupName ?: "Rider group") }
    var editingName by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showAddMembers by remember { mutableStateOf(false) }
    val isAdmin = myUid in conversation.groupAdmins

    LaunchedEffect(conversation.memberUids) {
        val map = HashMap<String, ChatProfile?>()
        conversation.memberUids.forEach { uid -> map[uid] = ChatRepository.getChatProfile(uid) }
        memberProfiles = map
    }

    Scaffold(
        containerColor = CameraGuardPalette.Background,
        topBar = {
            TopAppBar(
                title = { Text("Group info", color = CameraGuardPalette.Text) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CameraGuardPalette.Text) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CameraGuardPalette.Surface)
            )
        }
    ) { padding ->
        Column(modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(56.dp).background(CameraGuardPalette.AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = CameraGuardPalette.Accent, modifier = Modifier.size(28.dp))
                }
                if (editingName && isAdmin) {
                    OutlinedTextField(value = groupName, onValueChange = { groupName = it }, modifier = Modifier.weight(1f), singleLine = true)
                    IconButton(onClick = {
                        editingName = false
                        scope.launch { ChatRepository.renameGroup(conversation.id, groupName) }
                    }) { Icon(Icons.Default.Check, contentDescription = "Save name", tint = CameraGuardPalette.Accent) }
                } else {
                    Column(Modifier.weight(1f).let { if (isAdmin) it.clickable { editingName = true } else it }) {
                        Text(groupName, color = CameraGuardPalette.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${conversation.memberUids.size} members" + if (isAdmin) " · tap name to rename" else "", color = CameraGuardPalette.Muted, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (isAdmin) {
                OutlinedButton(onClick = { showAddMembers = true }, modifier = Modifier.fillMaxWidth()) { Text("Add members") }
                Spacer(Modifier.height(10.dp))
            }
            Text("Members", color = CameraGuardPalette.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.weight(1f)) {
                items(conversation.memberUids, key = { it }) { uid ->
                    val name = memberProfiles[uid]?.displayName ?: "Rider"
                    val memberPhotoUrl = memberProfiles[uid]?.photoUrl
                    val admin = uid in conversation.groupAdmins
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RiderAvatar(name, size = 36.dp, photoUrl = memberPhotoUrl)
                        Text(name + if (uid == myUid) " (you)" else "", color = CameraGuardPalette.Text, modifier = Modifier.weight(1f))
                        if (admin) Text("Admin", color = CameraGuardPalette.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedButton(
                onClick = { showLeaveConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CameraGuardPalette.Danger)
            ) { Text("Leave group") }
        }
    }

    if (showAddMembers) {
        AddMembersSheet(
            communityRiders = communityRiders.filter { it.uid !in conversation.memberUids },
            onDismiss = { showAddMembers = false },
            onAdd = { uids -> scope.launch { ChatRepository.addGroupMembers(conversation.id, uids) }; showAddMembers = false }
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave this group?") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    scope.launch { ChatRepository.leaveGroup(conversation.id) }
                    onLeft()
                }) { Text("Leave", color = CameraGuardPalette.Danger) }
            },
            dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") } },
            containerColor = CameraGuardPalette.Surface, titleContentColor = CameraGuardPalette.Text
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet is still experimental in Material3
@Composable
private fun AddMembersSheet(communityRiders: List<CommunityRider>, onDismiss: () -> Unit, onAdd: (Set<String>) -> Unit) {
    val selected = remember { mutableStateSetOf<String>() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CameraGuardPalette.Surface) {
        Column(Modifier.padding(16.dp)) {
            Text("Add members", color = CameraGuardPalette.Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (communityRiders.isEmpty()) {
                Text("No other riders online nearby right now.", color = CameraGuardPalette.Muted, fontSize = 12.sp)
            } else {
                communityRiders.forEach { rider ->
                    val isSelected = rider.uid in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { if (isSelected) selected.remove(rider.uid) else selected.add(rider.uid) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RiderAvatar(rider.displayName, size = 32.dp, photoUrl = rider.photoUrl)
                        Text(rider.displayName.ifBlank { "Rider" }, color = CameraGuardPalette.Text, modifier = Modifier.weight(1f))
                        Checkbox(checked = isSelected, onCheckedChange = { checked -> if (checked) selected.add(rider.uid) else selected.remove(rider.uid) })
                    }
                }
                Button(
                    onClick = { onAdd(selected.toSet()) }, enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CameraGuardPalette.Accent, contentColor = CameraGuardPalette.OnAccent)
                ) { Text("Add ${selected.size} rider(s)") }
            }
        }
    }
}
