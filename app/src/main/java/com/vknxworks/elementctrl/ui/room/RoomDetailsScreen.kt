package com.vknxworks.elementctrl.ui.room

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.elementctrl.matrix.AccountService
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.MemberInfo
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.ui.Avatar
import com.vknxworks.elementctrl.ui.ConfirmDialog
import com.vknxworks.elementctrl.ui.SectionHeader
import com.vknxworks.elementctrl.ui.SettingRow
import com.vknxworks.elementctrl.ui.TextInputDialog
import com.vknxworks.elementctrl.ui.copyText
import com.vknxworks.elementctrl.ui.shareText
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.UserProfile

@Composable
fun RoomDetailsScreen(
    session: MatrixSession,
    roomId: String,
    onBack: () -> Unit,
    onMembers: () -> Unit,
    onOpenUser: (String) -> Unit,
    onLeft: () -> Unit,
) {
    val vm: RoomViewModel = viewModel(key = "details:$roomId") { RoomViewModel(session, roomId, withTimeline = false) }
    val room = vm.room ?: run { UnavailableRoom(onBack); return }
    val header by room.header.collectAsStateWithLifecycle()
    val permissions by room.permissions.collectAsStateWithLifecycle()
    val error by room.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editName by remember { mutableStateOf(false) }
    var editTopic by remember { mutableStateOf(false) }
    var notifDialog by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmEncrypt by remember { mutableStateOf(false) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { room.setAvatar(context, it) } }

    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); room.clearError() } }

    val h = header
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (h?.isDm == true) "Chat info" else "Room info") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        if (h == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable(enabled = permissions.canEditAvatar && !h.isDm) {
                    pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Avatar(h.name, h.avatarUrl, h.dmUserId ?: h.id, size = 96.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text(h.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                (h.alias ?: h.dmUserId)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                h.topic?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (permissions.canInvite && !h.isDm) OutlinedButton(onClick = { inviting = true }) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Invite") }
                    OutlinedButton(onClick = { scope.launch { room.permalink()?.let { shareText(context, it) } } }) {
                        Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share")
                    }
                }
            }
            HorizontalDivider()

            if (h.isDm && h.dmUserId != null) {
                SettingRow(Icons.Default.Person, "View profile", h.dmUserId) { onOpenUser(h.dmUserId) }
            }
            SettingRow(
                Icons.Default.Notifications, "Notifications",
                when (h.notificationMode) {
                    null -> "Default"
                    RoomNotificationMode.ALL_MESSAGES -> "All messages"
                    RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY -> "Mentions and keywords"
                    RoomNotificationMode.MUTE -> "Off"
                },
            ) { notifDialog = true }
            SettingRow(Icons.Default.Favorite, "Favourite", trailing = { Switch(h.isFavourite, { room.setFavourite(it) }) }) { room.setFavourite(!h.isFavourite) }
            SettingRow(Icons.Default.SouthEast, "Low priority", trailing = { Switch(h.isLowPriority, { room.setLowPriority(it) }) }) { room.setLowPriority(!h.isLowPriority) }
            if (!h.isDm) {
                SettingRow(Icons.Default.Group, "People", "${h.joinedMembers} joined" + if (h.invitedMembers > 0) ", ${h.invitedMembers} invited" else "", onClick = onMembers)
            }
            if (h.pinnedEventIds.isNotEmpty()) {
                SettingRow(Icons.Default.PushPin, "Pinned messages", "${h.pinnedEventIds.size} pinned")
            }

            SectionHeader("Security")
            if (h.isEncrypted) {
                SettingRow(Icons.Default.Lock, "Encrypted", "Messages here are end-to-end encrypted. Only the people in the chat can read them.")
            } else {
                SettingRow(
                    Icons.Default.LockOpen, "Not encrypted",
                    if (h.isPublic) "Public rooms aren't encrypted." else "Turn on encryption so only members can read new messages. This can't be turned off.",
                    onClick = if (!h.isPublic) ({ confirmEncrypt = true }) else null,
                )
            }

            if (!h.isDm) {
                SectionHeader("Settings")
                if (permissions.canEditName) SettingRow(Icons.Default.Edit, "Room name", h.name) { editName = true }
                if (permissions.canEditTopic) SettingRow(Icons.Default.Edit, "Topic", h.topic ?: "Add a topic") { editTopic = true }
                if (permissions.canEditAvatar) SettingRow(Icons.Default.Image, "Room picture", "Change the room's picture") {
                    pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SettingRow(Icons.Default.Search, "Who can join", h.joinRule)
                SettingRow(Icons.Default.History, "Who can read history", h.historyVisibility)
                h.alias?.let { SettingRow(Icons.Default.Tag, "Address", it) { copyText(context, "Address", it) } }
            }
            SettingRow(Icons.Default.ContentCopy, "Copy room ID", h.id) { copyText(context, "Room ID", h.id); scope.launch { snackbar.showSnackbar("Room ID copied") } }
            SettingRow(Icons.Default.Link, "Copy link") { scope.launch { room.permalink()?.let { copyText(context, "Link", it); snackbar.showSnackbar("Link copied") } } }

            HorizontalDivider(Modifier.padding(top = 12.dp))
            SettingRow(
                Icons.AutoMirrored.Filled.ExitToApp, if (h.isDm) "Leave chat" else "Leave room",
                tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error,
            ) { confirmLeave = true }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (editName) TextInputDialog("Room name", initial = h?.name.orEmpty(), onDismiss = { editName = false }) { room.setName(it.trim()) }
    if (editTopic) TextInputDialog("Topic", initial = h?.topic.orEmpty(), singleLine = false, onDismiss = { editTopic = false }) { room.setTopic(it.trim()) }
    if (inviting) InviteDialog(session, onDismiss = { inviting = false }) { room.invite(it); scope.launch { snackbar.showSnackbar("Invited $it") } }
    if (confirmEncrypt) ConfirmDialog(
        "Turn on encryption?",
        "Once on, encryption can't be turned off. Bots and bridges that don't support encryption may stop working.",
        "Turn on",
        onDismiss = { confirmEncrypt = false },
        onConfirm = { room.enableEncryption() },
    )
    if (confirmLeave) ConfirmDialog(
        "Leave?",
        if (h?.isPublic == true) "You can rejoin later." else "If this room is private you'll need a new invite to come back.",
        "Leave",
        destructive = true,
        onDismiss = { confirmLeave = false },
        onConfirm = { room.leave(onLeft) },
    )
    if (notifDialog) {
        val options = listOf(
            null to "Default (match my settings)",
            RoomNotificationMode.ALL_MESSAGES to "All messages",
            RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY to "Mentions and keywords only",
            RoomNotificationMode.MUTE to "Off",
        )
        AlertDialog(
            onDismissRequest = { notifDialog = false },
            title = { Text("Notifications") },
            text = {
                Column {
                    options.forEach { (mode, label) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(h?.notificationMode == mode) { room.setNotificationMode(mode); notifDialog = false }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = h?.notificationMode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { notifDialog = false }) { Text("Close") } },
        )
    }
}

/** Search people by name or Matrix ID and pick one. */
@Composable
fun InviteDialog(session: MatrixSession, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val account = remember { AccountService(session) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.length >= 2) {
            kotlinx.coroutines.delay(300)
            results = runCatching { account.searchUsers(query) }.getOrDefault(emptyList())
        } else results = emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite people") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Name or @user:server") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(260.dp)) {
                    items(results, key = { it.userId }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onDismiss(); onPick(p.userId) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(p.displayName ?: p.userId, p.avatarUrl, p.userId, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(p.displayName ?: p.userId, style = MaterialTheme.typography.bodyLarge)
                                Text(p.userId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = query.startsWith("@") && query.contains(":"), onClick = { onDismiss(); onPick(query.trim()) }) { Text("Invite") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MembersScreen(session: MatrixSession, roomId: String, onBack: () -> Unit, onOpenUser: (String) -> Unit) {
    val vm: RoomViewModel = viewModel(key = "members:$roomId") { RoomViewModel(session, roomId, withTimeline = false) }
    val room = vm.room ?: run { UnavailableRoom(onBack); return }
    val members by room.members.collectAsStateWithLifecycle()
    val permissions by room.permissions.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var inviting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { room.loadMembers() }
    val filtered = remember(members, query) {
        members.filter { query.isBlank() || it.name.contains(query, true) || it.userId.contains(query, true) }
    }
    val groups = filtered.groupBy { it.membership }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("People") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (permissions.canInvite) IconButton(onClick = { inviting = true }) { Icon(Icons.Default.PersonAdd, "Invite") } },
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Search people") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            listOf("join" to "Joined", "invite" to "Invited", "knock" to "Asking to join", "ban" to "Banned").forEach { (key, label) ->
                val list = groups[key].orEmpty()
                if (list.isNotEmpty()) {
                    item(key) { SectionHeader("$label · ${list.size}") }
                    items(list, key = { "$key:${it.userId}" }) { m -> MemberRow(m) { onOpenUser(m.userId) } }
                }
            }
        }
    }
    if (inviting) InviteDialog(session, onDismiss = { inviting = false }) { room.invite(it) }
}

@Composable
private fun MemberRow(m: MemberInfo, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(m.name, m.avatarUrl, m.userId, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.bodyLarge)
            Text(m.userId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        m.role?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    }
}

/** Someone's profile: message them, verify them, ignore them, or moderate them in the room you came from. */
@Composable
fun UserScreen(
    session: MatrixSession,
    userId: String,
    fromRoomId: String?,
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onVerify: (String) -> Unit,
) {
    val account = remember { AccountService(session) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var ignored by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var moderate by remember { mutableStateOf<String?>(null) }
    val roomVm: RoomViewModel? = fromRoomId?.let { id -> viewModel(key = "user-room:$id") { RoomViewModel(session, id, withTimeline = false) } }
    val permissions = roomVm?.room?.permissions?.collectAsStateWithLifecycle()?.value

    LaunchedEffect(userId) {
        profile = account.profile(userId)
        ignored = runCatching { userId in account.ignoredUsers() }.getOrDefault(false)
        verified = runCatching { session.client.encryption().userIdentity(userId, true)?.isVerified() }.getOrNull()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Profile") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            val name = profile?.displayName ?: userId
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(name, profile?.avatarUrl, userId, size = 96.dp)
                Spacer(Modifier.height(12.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(userId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (verified == true) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("Verified", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (userId != session.userId) {
                    Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            runCatching { account.startDm(userId) }
                                .onSuccess { onOpenRoom(it) }
                                .onFailure { snackbar.showSnackbar(it.userMessage()) }
                            busy = false
                        }
                    }) { Text("Send message") }
                }
            }
            HorizontalDivider()
            if (userId != session.userId && verified != true) {
                SettingRow(Icons.Default.VerifiedUser, "Verify ${profile?.displayName ?: "user"}", "Compare emojis to make sure you're talking to the right person.") { onVerify(userId) }
            }
            SettingRow(Icons.Default.ContentCopy, "Copy Matrix ID", userId) { copyText(context, "Matrix ID", userId) }
            SettingRow(Icons.Default.Share, "Share profile link") {
                shareText(context, "https://matrix.to/#/$userId")
            }
            if (userId != session.userId) {
                SettingRow(
                    Icons.Default.Block, if (ignored) "Unignore" else "Ignore",
                    if (ignored) "You'll see their messages again." else "Hide their messages and invites everywhere.",
                    tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error,
                ) {
                    scope.launch {
                        runCatching { if (ignored) account.unignore(userId) else account.ignore(userId) }
                            .onSuccess { ignored = !ignored }
                            .onFailure { snackbar.showSnackbar(it.userMessage()) }
                    }
                }
            }
            if (permissions != null && userId != session.userId && (permissions.canKick || permissions.canBan)) {
                SectionHeader("In this room")
                if (permissions.canKick) SettingRow(Icons.Default.PersonRemove, "Remove from room", tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) { moderate = "kick" }
                if (permissions.canBan) SettingRow(Icons.Default.Block, "Ban from room", tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) { moderate = "ban" }
            }
        }
    }
    moderate?.let { action ->
        TextInputDialog(
            if (action == "kick") "Remove from room" else "Ban from room",
            label = "Reason (optional)",
            confirm = if (action == "kick") "Remove" else "Ban",
            onDismiss = { moderate = null },
        ) { reason ->
            val r = roomVm?.room ?: return@TextInputDialog
            if (action == "kick") r.kick(userId, reason.ifBlank { null }) else r.ban(userId, reason.ifBlank { null })
        }
    }
}

@Composable
private fun UnavailableRoom(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Room") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            Text("This room isn't available yet.", modifier = Modifier.padding(32.dp))
        }
    }
}
