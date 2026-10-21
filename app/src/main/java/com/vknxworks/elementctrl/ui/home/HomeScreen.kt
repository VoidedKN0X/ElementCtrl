package com.vknxworks.elementctrl.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.RoomSummary
import com.vknxworks.elementctrl.ui.Avatar
import com.vknxworks.elementctrl.ui.CenteredLoading
import com.vknxworks.elementctrl.ui.ConfirmDialog
import com.vknxworks.elementctrl.ui.EmptyState
import com.vknxworks.elementctrl.ui.SettingRow
import com.vknxworks.elementctrl.ui.UnreadBadge
import com.vknxworks.elementctrl.ui.shortTime
import com.vknxworks.elementctrl.ui.TabTitle
import com.vknxworks.elementctrl.ui.LocalBottomClearance
import com.vknxworks.elementctrl.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.SyncServiceState
import org.matrix.rustcomponents.sdk.VerificationState

enum class RoomFilter(@androidx.annotation.StringRes val label: Int) {
    ALL(R.string.filter_all), UNREAD(R.string.filter_unread), PEOPLE(R.string.filter_people), ROOMS(R.string.filter_rooms),
    FAVOURITES(R.string.filter_favourites), INVITES(R.string.filter_invites),
}

@Composable
fun HomeScreen(
    session: MatrixSession,
    onOpenRoom: (String) -> Unit,
    onNewChat: () -> Unit,
    /** Your profile picture at the top right: opens the You page (account and settings). */
    onProfile: () -> Unit,
    onSecurity: () -> Unit,
    /** The chat open beside the list on tablets, highlighted. */
    selectedRoomId: String? = null,
) {
    val rooms by session.rooms.collectAsStateWithLifecycle()
    val drafts by com.vknxworks.elementctrl.ElementCtrlApp.graph.drafts.all.collectAsStateWithLifecycle()
    val loaded by session.roomsLoaded.collectAsStateWithLifecycle()
    val sync by session.syncState.collectAsStateWithLifecycle()
    val profile by session.profile.collectAsStateWithLifecycle()
    val verification by session.verification.collectAsStateWithLifecycle()
    val recovery by session.recovery.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var filter by rememberSaveable { mutableStateOf(RoomFilter.ALL) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<RoomSummary?>(null) }
    var leaving by remember { mutableStateOf<RoomSummary?>(null) }

    val visible = remember(rooms, filter, query) {
        val q = query.trim().lowercase()
        rooms.filter { r ->
            !r.isSpace && (q.isEmpty() || q in r.searchText) && when (filter) {
                RoomFilter.ALL -> true
                RoomFilter.UNREAD -> r.hasUnread || r.isInvite
                RoomFilter.PEOPLE -> r.isDm && !r.isInvite
                RoomFilter.ROOMS -> !r.isDm && !r.isInvite
                RoomFilter.FAVOURITES -> r.isFavourite
                RoomFilter.INVITES -> r.isInvite
            }
        }.sortedWith(compareByDescending<RoomSummary> { it.isInvite }.thenBy { it.isLowPriority })
    }
    val inviteCount = rooms.count { it.isInvite }

    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(nearEnd, rooms.size) {
        if (rooms.size >= MatrixSession.PAGE_SIZE && nearEnd >= rooms.size - 10) session.loadMoreRooms()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // On phones the list runs under the floating glass tab bar (which pads for the navigation bar itself).
        contentWindowInsets = if (LocalBottomClearance.current > 0.dp) androidx.compose.foundation.layout.WindowInsets.statusBars
        else androidx.compose.material3.ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.chats_search_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    navigationIcon = { IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.chats_close_search)) } },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            } else {
                // The Ctrl series tab title: big "Chats" with your profile picture at the top right.
                TabTitle(stringResource(R.string.tab_chats), session, onProfile, Modifier.statusBarsPadding()) {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, stringResource(R.string.chats_search)) }
                }
            }
        },
        floatingActionButton = {
            // Sits above the floating glass tab bar on phones.
            val clearance = LocalBottomClearance.current
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                icon = { Icon(Icons.Default.Edit, null) },
                text = { Text(stringResource(R.string.chats_new)) },
                expanded = listState.firstVisibleItemIndex == 0,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = if (clearance > 0.dp) clearance - 4.dp else 0.dp),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            val stuck by session.stuck.collectAsStateWithLifecycle()
            AnimatedVisibility(stuck || sync == SyncServiceState.OFFLINE || sync == SyncServiceState.ERROR) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            stuck -> stringResource(R.string.chats_unreachable)
                            sync == SyncServiceState.OFFLINE -> stringResource(R.string.chats_offline)
                            else -> stringResource(R.string.chats_retrying)
                        },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    // Reloads the app's connection, the same as force-stopping and reopening it.
                    androidx.compose.material3.TextButton(onClick = {
                        com.vknxworks.elementctrl.matrix.ConnectionLog.log("Reconnect tapped (sync $sync)")
                        com.vknxworks.elementctrl.AppRestart.restart(ctx)
                    }) { Text(stringResource(R.string.chats_reconnect), color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }
            if (!loaded && rooms.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            val sharing by com.vknxworks.elementctrl.ui.DeepLinks.share.collectAsStateWithLifecycle()
            if (sharing != null) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.chats_share_to), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    IconButton(onClick = { com.vknxworks.elementctrl.ui.DeepLinks.share.value = null }) { Icon(Icons.Default.Close, stringResource(R.string.chats_cancel_share)) }
                }
            }

            val ctx = androidx.compose.ui.platform.LocalContext.current
            val notificationsOn by com.vknxworks.elementctrl.ElementCtrlApp.graph.prefs.notifications.collectAsStateWithLifecycle()
            val health = com.vknxworks.elementctrl.ui.rememberPushHealth()
            val bannerTick by com.vknxworks.elementctrl.notify.Battery.bannerTick.collectAsStateWithLifecycle()
            val showBattery = remember(notificationsOn, health, bannerTick) {
                com.vknxworks.elementctrl.notify.Battery.shouldShowBanner(ctx, notificationsOn)
            }
            val appHeldBack = !health.appUnrestricted || health.appBackgroundRestricted
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp + LocalBottomClearance.current)) {
                if (showBattery) {
                    item("battery") {
                        Banner(
                            icon = Icons.Default.BatteryAlert,
                            title = stringResource(R.string.banner_battery_title),
                            body = stringResource(if (appHeldBack) R.string.banner_battery_app else R.string.banner_battery_ntfy),
                            action = stringResource(if (appHeldBack) R.string.warn_allow else R.string.banner_fix),
                            onClick = {
                                if (appHeldBack) com.vknxworks.elementctrl.notify.Battery.requestUnrestricted(ctx)
                                else com.vknxworks.elementctrl.notify.Battery.openNtfyAppInfo(ctx)
                            },
                            onDismiss = { com.vknxworks.elementctrl.notify.Battery.dismissBanner(ctx) },
                        )
                    }
                }
                if (verification == VerificationState.UNVERIFIED) {
                    item("verify") {
                        Banner(
                            icon = Icons.Default.VerifiedUser,
                            title = stringResource(R.string.banner_verify_title),
                            body = stringResource(R.string.banner_verify_body),
                            action = stringResource(R.string.banner_verify),
                            onClick = onSecurity,
                        )
                    }
                } else if (verification == VerificationState.VERIFIED && (recovery == RecoveryState.DISABLED || recovery == RecoveryState.INCOMPLETE)) {
                    item("recovery") {
                        Banner(
                            icon = Icons.Default.Security,
                            title = stringResource(if (recovery == RecoveryState.INCOMPLETE) R.string.banner_recovery_incomplete_title else R.string.banner_recovery_title),
                            body = stringResource(R.string.banner_recovery_body),
                            action = stringResource(R.string.banner_set_up),
                            onClick = onSecurity,
                        )
                    }
                }
                item("filters") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(RoomFilter.entries.filter { it != RoomFilter.INVITES || inviteCount > 0 }) { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = if (filter == f) RoomFilter.ALL else f },
                                label = { Text(if (f == RoomFilter.INVITES) stringResource(f.label, inviteCount) else stringResource(f.label)) },
                            )
                        }
                    }
                }
                if (loaded && visible.isEmpty()) {
                    item("empty") {
                        EmptyState(
                            Icons.AutoMirrored.Filled.Chat,
                            stringResource(if (query.isNotBlank() || filter != RoomFilter.ALL) R.string.chats_nothing else R.string.chats_empty),
                            stringResource(if (query.isNotBlank() || filter != RoomFilter.ALL) R.string.chats_nothing_body else R.string.chats_empty_body),
                        )
                    }
                } else if (!loaded && rooms.isEmpty()) {
                    item("loading") { CenteredLoading(Modifier.fillMaxWidth().height(200.dp)) }
                }
                items(visible, key = { it.roomId }) { room ->
                    if (room.isInvite) {
                        InviteRow(
                            room,
                            onOpen = { onOpenRoom(room.roomId) },
                            onAccept = {
                                scope.launch {
                                    runCatching { session.getRoom(room.roomId)?.use { it.join() } }
                                    session.refreshRoom(room.roomId)
                                }
                            },
                            onDecline = {
                                scope.launch {
                                    runCatching { session.getRoom(room.roomId)?.use { it.leave() } }
                                }
                            },
                        )
                    } else {
                        RoomRow(
                            room,
                            draft = drafts[room.roomId]?.takeIf { room.roomId != selectedRoomId },
                            selected = room.roomId == selectedRoomId,
                            onClick = { onOpenRoom(room.roomId) },
                            onLongClick = { menuFor = room },
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { room ->
        ModalBottomSheet(onDismissRequest = { menuFor = null }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(room.name, room.avatarUrl, room.roomId, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(room.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider()
            fun withRoom(block: suspend (org.matrix.rustcomponents.sdk.Room) -> Unit) {
                menuFor = null
                scope.launch {
                    runCatching { session.getRoom(room.roomId)?.use { block(it) } }
                    session.refreshRoom(room.roomId)
                }
            }
            if (room.hasUnread) {
                SettingRow(Icons.Default.MarkChatRead, stringResource(R.string.room_mark_read)) {
                    withRoom { it.markAsRead(ReceiptType.READ); it.setUnreadFlag(false) }
                }
            } else {
                SettingRow(Icons.Default.MarkChatUnread, stringResource(R.string.room_mark_unread)) { withRoom { it.setUnreadFlag(true) } }
            }
            SettingRow(
                if (room.isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                stringResource(if (room.isFavourite) R.string.room_favourite_remove else R.string.room_favourite_add),
            ) { withRoom { it.setIsFavourite(!room.isFavourite, null) } }
            SettingRow(Icons.Default.SouthEast, stringResource(if (room.isLowPriority) R.string.room_low_priority_remove else R.string.room_low_priority)) {
                withRoom { it.setIsLowPriority(!room.isLowPriority, null) }
            }
            SettingRow(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.room_leave), tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) {
                leaving = room; menuFor = null
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    leaving?.let { room ->
        ConfirmDialog(
            title = stringResource(R.string.room_leave_title, room.name),
            text = stringResource(if (room.isDm) R.string.room_leave_dm else R.string.room_leave_room),
            confirm = stringResource(R.string.room_leave),
            destructive = true,
            onDismiss = { leaving = null },
            onConfirm = { scope.launch { runCatching { session.getRoom(room.roomId)?.use { it.leave() } } } },
        )
    }
}

@Composable
private fun Banner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onClick) { Text(action) }
                if (onDismiss != null) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.banner_not_now), color = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
            }
        }
    }
}

@Composable
private fun RoomRow(room: RoomSummary, draft: String?, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val draftLabel = stringResource(R.string.room_draft)
    val youLabel = stringResource(R.string.room_you)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(room.name, room.avatarUrl, room.dmUserId ?: room.roomId, size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Name and lock take all the room left over, so the time always sits flush against the right edge.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        room.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (room.hasUnread && !room.isMuted) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (room.isEncrypted && !room.isDm) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Lock, stringResource(R.string.room_encrypted), Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                room.lastTimestamp?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        shortTime(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (room.hasUnread && !room.isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (room.lastFailed) {
                    Icon(Icons.Default.ErrorOutline, stringResource(R.string.room_not_sent), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    if (draft != null) {
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)) { append(draftLabel) }
                            append(draft)
                        }
                    } else androidx.compose.ui.text.AnnotatedString(buildString {
                        if (!room.isDm && room.lastSender != null) append(room.lastSender).append(": ")
                        else if (room.isDm && room.lastIsOwn && room.lastSender != null) append(youLabel)
                        append(room.lastMessage ?: room.topic ?: "")
                    }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (room.isMuted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.NotificationsOff, stringResource(R.string.room_muted), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (room.isFavourite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Favorite, stringResource(R.string.room_favourite), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (room.hasUnread) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge(room.unreadNotifications, highlight = room.unreadMentions > 0, muted = room.isMuted)
                }
            }
        }
    }
}

@Composable
private fun InviteRow(room: RoomSummary, onOpen: () -> Unit, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(room.name, room.avatarUrl, room.roomId, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (room.isDm) R.string.invite_from_dm else R.string.invite_from, room.inviterName ?: stringResource(R.string.invite_someone)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            UnreadBadge(0, highlight = true, muted = false)
        }
        Row(Modifier.padding(start = 66.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDecline) { Text(stringResource(R.string.invite_decline)) }
            Button(onClick = onAccept) { Text(stringResource(R.string.invite_accept)) }
        }
    }
}
