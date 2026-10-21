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
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.res.stringResource
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.ui.ScreenTopBar

@Composable
fun RoomDetailsScreen(
    session: MatrixSession,
    roomId: String,
    onBack: () -> Unit,
    onMembers: () -> Unit,
    onOpenUser: (String) -> Unit,
    onLeft: () -> Unit,
    onMedia: () -> Unit = {},
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
            ScreenTopBar(
                title = if (h?.isDm == true) stringResource(R.string.chat_info) else stringResource(R.string.room_info),
                onBack = onBack,
            )
        },
    ) { pad ->
        if (h == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable(enabled = permissions.canEditAvatar && !h.isDm) {
                    com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn(); pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
                    if (permissions.canInvite && !h.isDm) OutlinedButton(onClick = { inviting = true }) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.invite)) }
                    OutlinedButton(onClick = { scope.launch { room.permalink()?.let { shareText(context, it) } } }) {
                        Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share))
                    }
                }
            }
            HorizontalDivider()

            if (h.isDm && h.dmUserId != null) {
                SettingRow(Icons.Default.Person, stringResource(R.string.view_profile), h.dmUserId) { onOpenUser(h.dmUserId) }
            }
            SettingRow(
                Icons.Default.Notifications, stringResource(R.string.group_notifications),
                when (h.notificationMode) {
                    null -> Strings.str(R.string.default_text)
                    RoomNotificationMode.ALL_MESSAGES -> Strings.str(R.string.mode_all)
                    RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY -> Strings.str(R.string.mode_mentions)
                    RoomNotificationMode.MUTE -> Strings.str(R.string.mode_off)
                },
            ) { notifDialog = true }
            SettingRow(Icons.Default.PhotoLibrary, stringResource(R.string.media_and_files), stringResource(R.string.photos_videos_files_and_voice), onClick = onMedia)
            SettingRow(Icons.Default.Favorite, stringResource(R.string.room_favourite), trailing = { Switch(h.isFavourite, { room.setFavourite(it) }) }) { room.setFavourite(!h.isFavourite) }
            SettingRow(Icons.Default.SouthEast, stringResource(R.string.room_low_priority), trailing = { Switch(h.isLowPriority, { room.setLowPriority(it) }) }) { room.setLowPriority(!h.isLowPriority) }
            if (!h.isDm) {
                SettingRow(Icons.Default.Group, stringResource(R.string.filter_people), if (h.invitedMembers > 0) stringResource(R.string.members_joined_invited, h.joinedMembers, h.invitedMembers) else stringResource(R.string.members_joined, h.joinedMembers), onClick = onMembers)
            }
            if (h.pinnedEventIds.isNotEmpty()) {
                SettingRow(Icons.Default.PushPin, stringResource(R.string.pinned_messages), stringResource(R.string.pinned, h.pinnedEventIds.size))
            }

            SectionHeader(stringResource(R.string.security))
            if (h.isEncrypted) {
                SettingRow(Icons.Default.Lock, stringResource(R.string.room_encrypted), stringResource(R.string.messages_here_are_end_to_end_2))
            } else {
                SettingRow(
                    Icons.Default.LockOpen, stringResource(R.string.not_encrypted_2),
                    if (h.isPublic) stringResource(R.string.public_rooms_aren_t_encrypted) else stringResource(R.string.turn_on_encryption_so_only_members),
                    onClick = if (!h.isPublic) ({ confirmEncrypt = true }) else null,
                )
            }

            if (!h.isDm) {
                SectionHeader(stringResource(R.string.settings))
                if (permissions.canEditName) SettingRow(Icons.Default.Edit, stringResource(R.string.room_name), h.name) { editName = true }
                if (permissions.canEditTopic) SettingRow(Icons.Default.Edit, stringResource(R.string.topic), h.topic ?: stringResource(R.string.add_a_topic)) { editTopic = true }
                if (permissions.canEditAvatar) SettingRow(Icons.Default.Image, stringResource(R.string.room_picture), stringResource(R.string.change_the_room_s_picture)) {
                    com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn(); pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SettingRow(Icons.Default.Search, stringResource(R.string.who_can_join), h.joinRule)
                SettingRow(Icons.Default.History, stringResource(R.string.who_can_read_history), h.historyVisibility)
                h.alias?.let { SettingRow(Icons.Default.Tag, stringResource(R.string.address), it) { copyText(context, Strings.str(R.string.address), it) } }
            }
            SettingRow(Icons.Default.ContentCopy, stringResource(R.string.copy_room_id), h.id) { copyText(context, Strings.str(R.string.room_id), h.id); scope.launch { snackbar.showSnackbar(Strings.str(R.string.room_id_copied)) } }
            SettingRow(Icons.Default.Link, stringResource(R.string.copy_link)) { scope.launch { room.permalink()?.let { copyText(context, Strings.str(R.string.link), it); snackbar.showSnackbar(Strings.str(R.string.link_copied)) } } }

            HorizontalDivider(Modifier.padding(top = 12.dp))
            SettingRow(
                Icons.AutoMirrored.Filled.ExitToApp, if (h.isDm) stringResource(R.string.leave_chat) else stringResource(R.string.leave_room),
                tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error,
            ) { confirmLeave = true }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (editName) TextInputDialog(stringResource(R.string.room_name), initial = h?.name.orEmpty(), onDismiss = { editName = false }) { room.setName(it.trim()) }
    if (editTopic) TextInputDialog(stringResource(R.string.topic), initial = h?.topic.orEmpty(), singleLine = false, onDismiss = { editTopic = false }) { room.setTopic(it.trim()) }
    if (inviting) InviteDialog(session, onDismiss = { inviting = false }) { room.invite(it); scope.launch { snackbar.showSnackbar(Strings.str(R.string.invited_3, it)) } }
    if (confirmEncrypt) ConfirmDialog(
        stringResource(R.string.turn_on_encryption),
        stringResource(R.string.once_on_encryption_can_t_be_turned),
        stringResource(R.string.turn_on),
        onDismiss = { confirmEncrypt = false },
        onConfirm = { room.enableEncryption() },
    )
    if (confirmLeave) ConfirmDialog(
        stringResource(R.string.leave),
        if (h?.isPublic == true) stringResource(R.string.you_can_rejoin_later) else stringResource(R.string.if_this_room_is_private_you_ll_need),
        stringResource(R.string.room_leave),
        destructive = true,
        onDismiss = { confirmLeave = false },
        onConfirm = { room.leave(onLeft) },
    )
    if (notifDialog) {
        val options = listOf(
            null to Strings.str(R.string.default_match_my_settings),
            RoomNotificationMode.ALL_MESSAGES to Strings.str(R.string.mode_all),
            RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY to Strings.str(R.string.mentions_and_keywords_only),
            RoomNotificationMode.MUTE to Strings.str(R.string.mode_off),
        )
        AlertDialog(
            onDismissRequest = { notifDialog = false },
            title = { Text(stringResource(R.string.group_notifications)) },
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
            confirmButton = { TextButton(onClick = { notifDialog = false }) { Text(stringResource(R.string.lock_close)) } },
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
        title = { Text(stringResource(R.string.invite_people)) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.name_or_user_server)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
            TextButton(enabled = query.startsWith("@") && query.contains(":"), onClick = { onDismiss(); onPick(query.trim()) }) { Text(stringResource(R.string.invite)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
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
            ScreenTopBar(
                title = stringResource(R.string.filter_people),
                onBack = onBack,
                actions = { if (permissions.canInvite) IconButton(onClick = { inviting = true }) { Icon(Icons.Default.PersonAdd, stringResource(R.string.invite)) } },
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text(stringResource(R.string.search_people)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            listOf("join" to Strings.str(R.string.joined_2), "invite" to Strings.str(R.string.invited_4), "knock" to Strings.str(R.string.asking_to_join), "ban" to Strings.str(R.string.banned)).forEach { (key, label) ->
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
        topBar = { ScreenTopBar(stringResource(R.string.profile), onBack = onBack) },
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
                        Text(stringResource(R.string.verified), color = MaterialTheme.colorScheme.primary)
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
                    }) { Text(stringResource(R.string.send_message)) }
                }
            }
            HorizontalDivider()
            if (userId != session.userId && verified != true) {
                SettingRow(Icons.Default.VerifiedUser, stringResource(R.string.verify_2, profile?.displayName ?: userId), stringResource(R.string.compare_emojis_to_make_sure_you_re)) { onVerify(userId) }
            }
            SettingRow(Icons.Default.ContentCopy, stringResource(R.string.copy_matrix_id), userId) { copyText(context, Strings.str(R.string.matrix_id), userId) }
            SettingRow(Icons.Default.Share, stringResource(R.string.share_profile_link)) {
                shareText(context, "https://matrix.to/#/$userId")
            }
            if (userId != session.userId) {
                SettingRow(
                    Icons.Default.Block, if (ignored) stringResource(R.string.unignore) else stringResource(R.string.ignore),
                    if (ignored) stringResource(R.string.you_ll_see_their_messages_again) else stringResource(R.string.hide_their_messages_and_invites),
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
                SectionHeader(stringResource(R.string.in_this_room))
                if (permissions.canKick) SettingRow(Icons.Default.PersonRemove, stringResource(R.string.remove_from_room), tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) { moderate = "kick" }
                if (permissions.canBan) SettingRow(Icons.Default.Block, stringResource(R.string.ban_from_room), tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) { moderate = "ban" }
            }
        }
    }
    moderate?.let { action ->
        TextInputDialog(
            if (action == "kick") stringResource(R.string.remove_from_room) else stringResource(R.string.ban_from_room),
            label = stringResource(R.string.reason_optional),
            confirm = if (action == "kick") stringResource(R.string.remove) else stringResource(R.string.ban),
            onDismiss = { moderate = null },
        ) { reason ->
            val r = roomVm?.room ?: return@TextInputDialog
            if (action == "kick") r.kick(userId, reason.ifBlank { null }) else r.ban(userId, reason.ifBlank { null })
        }
    }
}

@Composable
private fun UnavailableRoom(onBack: () -> Unit) {
    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.room_2), onBack = onBack) }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.this_room_isn_t_available_yet), modifier = Modifier.padding(32.dp))
        }
    }
}
