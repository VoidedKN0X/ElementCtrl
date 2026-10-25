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
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SouthEast
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Videocam
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
import com.vknxworks.elementctrl.ui.CtrlColors
import com.vknxworks.elementctrl.ui.CtrlShapes
import com.vknxworks.elementctrl.ui.CtrlType
import com.vknxworks.elementctrl.ui.Gutter
import com.vknxworks.elementctrl.ui.components.CtrlButton
import com.vknxworks.elementctrl.ui.components.CtrlCard
import com.vknxworks.elementctrl.ui.components.CtrlHeroCard
import com.vknxworks.elementctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.elementctrl.ui.components.CtrlSheet
import com.vknxworks.elementctrl.ui.components.CtrlSquareButton
import com.vknxworks.elementctrl.ui.components.CtrlTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.FilterChipDefaults
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
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CtrlSquareButton(onClick = { searching = false; query = "" }, contentDescription = stringResource(R.string.chats_close_search)) {
                        Icon(Icons.Outlined.Close, null, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    CtrlTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.chats_search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                // The Ctrl series tab title: big "Chats" with your profile picture at the top right.
                TabTitle(stringResource(R.string.tab_chats), session, onProfile) {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, stringResource(R.string.chats_search), Modifier.size(22.dp), tint = CtrlColors.Muted) }
                }
            }
        },
        floatingActionButton = {
            // Sits above the floating glass tab bar on phones.
            val clearance = LocalBottomClearance.current
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                icon = { Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp)) },
                text = { Text(stringResource(R.string.chats_new)) },
                expanded = listState.firstVisibleItemIndex == 0,
                shape = CtrlShapes.Cta,
                containerColor = CtrlColors.Accent,
                contentColor = CtrlColors.OnAccent,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier.padding(bottom = if (clearance > 0.dp) clearance - 12.dp else 0.dp),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            val stuck by session.stuck.collectAsStateWithLifecycle()
            AnimatedVisibility(stuck || sync == SyncServiceState.OFFLINE || sync == SyncServiceState.ERROR) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp)
                        .clip(CtrlShapes.Control).background(MaterialTheme.colorScheme.errorContainer)
                        .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
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
            if (!loaded && rooms.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Gutter).height(6.dp).clip(RoundedCornerShape(3.dp)), color = CtrlColors.AccentText, trackColor = CtrlColors.ProgressTrack)
            val sharing by com.vknxworks.elementctrl.ui.DeepLinks.share.collectAsStateWithLifecycle()
            if (sharing != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp)
                        .clip(CtrlShapes.Control).background(CtrlColors.Surface).border(1.dp, CtrlColors.Border, CtrlShapes.Control)
                        .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.chats_share_to), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    IconButton(onClick = { com.vknxworks.elementctrl.ui.DeepLinks.share.value = null }) { Icon(Icons.Outlined.Close, stringResource(R.string.chats_cancel_share)) }
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
            // Android 13+ only shows an app's notifications once it has asked for permission. Ask once, right after
            // sign-in; after that a banner explains it and opens the prompt (or Android's settings if it's been denied).
            val access = com.vknxworks.elementctrl.notify.NotificationAccess
            var notificationsAllowed by remember { mutableStateOf(health.notificationsAllowed) }
            var autoAsk by remember { mutableStateOf(false) }
            LaunchedEffect(health) { notificationsAllowed = health.notificationsAllowed }
            val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted ->
                notificationsAllowed = access.allowed(ctx)
                // Allow tapped but Android won't show the prompt any more (denied before): open the app's notification
                // settings instead. Not after the automatic first ask: "Don't allow" there is respected.
                if (!granted && !autoAsk) access.openSettings(ctx)
                autoAsk = false
            }
            fun requestNotifications() {
                if (!access.hasPermission(ctx)) {
                    com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn()
                    askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else access.openSettings(ctx)
            }
            LaunchedEffect(notificationsOn) {
                if (notificationsOn && !access.hasPermission(ctx) && !access.askedAutomatically(ctx)) {
                    access.markAskedAutomatically(ctx)
                    autoAsk = true
                    com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn()
                    askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp + LocalBottomClearance.current)) {
                if (notificationsOn && !notificationsAllowed) {
                    item("notifications-blocked") {
                        Banner(
                            icon = Icons.Outlined.NotificationsOff,
                            title = stringResource(R.string.banner_notifications_blocked_title),
                            body = stringResource(R.string.banner_notifications_blocked_body),
                            action = stringResource(R.string.warn_allow),
                            onClick = { requestNotifications() },
                        )
                    }
                }
                if (showBattery) {
                    item("battery") {
                        Banner(
                            icon = Icons.Outlined.BatteryAlert,
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
                            icon = Icons.Outlined.VerifiedUser,
                            title = stringResource(R.string.banner_verify_title),
                            body = stringResource(R.string.banner_verify_body),
                            action = stringResource(R.string.banner_verify),
                            onClick = onSecurity,
                        )
                    }
                } else if (verification == VerificationState.VERIFIED && (recovery == RecoveryState.DISABLED || recovery == RecoveryState.INCOMPLETE)) {
                    item("recovery") {
                        Banner(
                            icon = Icons.Outlined.Security,
                            title = stringResource(if (recovery == RecoveryState.INCOMPLETE) R.string.banner_recovery_incomplete_title else R.string.banner_recovery_title),
                            body = stringResource(R.string.banner_recovery_body),
                            action = stringResource(R.string.banner_set_up),
                            onClick = onSecurity,
                        )
                    }
                }
                if (!searching && loaded) {
                    item("hero") {
                        // The one number that matters on the chat list: how many chats have something new.
                        val unread = rooms.count { !it.isSpace && (it.hasUnread || it.isInvite) }
                        val chats = rooms.count { !it.isSpace }
                        CtrlHeroCard(
                            label = stringResource(R.string.hero_unread),
                            trailingLabel = pluralStringResource(R.plurals.hero_chats, chats, chats),
                            value = unread.toString(),
                            modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                        )
                    }
                }
                item("filters") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(RoomFilter.entries.filter { it != RoomFilter.INVITES || inviteCount > 0 }) { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = if (filter == f) RoomFilter.ALL else f },
                                label = { Text(if (f == RoomFilter.INVITES) stringResource(f.label, inviteCount) else stringResource(f.label)) },
                                shape = CtrlShapes.Badge,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = CtrlColors.Surface,
                                    labelColor = CtrlColors.Muted,
                                    selectedContainerColor = CtrlColors.Accent,
                                    selectedLabelColor = CtrlColors.OnAccent,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true, selected = filter == f,
                                    borderColor = CtrlColors.ControlBorder, selectedBorderColor = CtrlColors.Accent,
                                    borderWidth = 1.dp, selectedBorderWidth = 1.dp,
                                ),
                            )
                        }
                    }
                }
                if (loaded && visible.isEmpty()) {
                    item("empty") {
                        EmptyState(
                            Icons.AutoMirrored.Outlined.Chat,
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
        CtrlSheet(onDismissRequest = { menuFor = null }) {
            Row(Modifier.padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(room.name, room.avatarUrl, room.roomId, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(room.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = CtrlColors.Border)
            fun withRoom(block: suspend (org.matrix.rustcomponents.sdk.Room) -> Unit) {
                menuFor = null
                scope.launch {
                    runCatching { session.getRoom(room.roomId)?.use { block(it) } }
                    session.refreshRoom(room.roomId)
                }
            }
            if (room.hasUnread) {
                SettingRow(Icons.Outlined.MarkChatRead, stringResource(R.string.room_mark_read)) {
                    withRoom { it.markAsRead(ReceiptType.READ); it.setUnreadFlag(false) }
                }
            } else {
                SettingRow(Icons.Outlined.MarkChatUnread, stringResource(R.string.room_mark_unread)) { withRoom { it.setUnreadFlag(true) } }
            }
            SettingRow(
                if (room.isFavourite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                stringResource(if (room.isFavourite) R.string.room_favourite_remove else R.string.room_favourite_add),
            ) { withRoom { it.setIsFavourite(!room.isFavourite, null) } }
            SettingRow(Icons.Outlined.SouthEast, stringResource(if (room.isLowPriority) R.string.room_low_priority_remove else R.string.room_low_priority)) {
                withRoom { it.setIsLowPriority(!room.isLowPriority, null) }
            }
            SettingRow(Icons.AutoMirrored.Outlined.ExitToApp, stringResource(R.string.room_leave), tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) {
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
    CtrlCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
        padding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = CtrlColors.AccentText)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CtrlButton(onClick = onClick) { Text(action) }
                if (onDismiss != null) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.banner_not_now), color = CtrlColors.Muted) }
                }
            }
        }
    }
}

@Composable
private fun RoomRow(room: RoomSummary, draft: String?, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val draftLabel = stringResource(R.string.room_draft)
    val youLabel = stringResource(R.string.room_you)
    // Plain Ctrl list row with a 1 dp divider on top.
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Gutter).height(1.dp).background(CtrlColors.Divider))
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) CtrlColors.Surface else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .heightIn(min = 52.dp)
                .padding(horizontal = Gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(room.name, room.avatarUrl, room.dmUserId ?: room.roomId, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Name and lock take all the room left over, so the time always sits flush against the right edge.
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            room.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (room.hasUnread && !room.isMuted) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (room.isEncrypted && !room.isDm) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.Lock, stringResource(R.string.room_encrypted), Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    room.lastTimestamp?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            shortTime(it).uppercase(),
                            style = CtrlType.Status,
                            color = if (room.hasUnread && !room.isMuted) CtrlColors.AccentText else CtrlColors.Disabled,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (room.lastFailed) {
                        Icon(Icons.Outlined.ErrorOutline, stringResource(R.string.room_not_sent), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
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
                    if (room.hasCall) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Outlined.Videocam, stringResource(R.string.call_room_icon), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (room.isMuted) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Outlined.NotificationsOff, stringResource(R.string.room_muted), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (room.isFavourite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Outlined.Favorite, stringResource(R.string.room_favourite), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (room.hasUnread) {
                        Spacer(Modifier.width(8.dp))
                        UnreadBadge(room.unreadNotifications, highlight = room.unreadMentions > 0, muted = room.isMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteRow(room: RoomSummary, onOpen: () -> Unit, onAccept: () -> Unit, onDecline: () -> Unit) {
    // Plain Ctrl list row with a 1 dp divider on top.
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Gutter).height(1.dp).background(CtrlColors.Divider))
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen)
                .padding(horizontal = Gutter, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(room.name, room.avatarUrl, room.roomId, size = 48.dp)
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
            Row(Modifier.padding(start = 62.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CtrlSecondaryButton(onClick = onDecline) { Text(stringResource(R.string.invite_decline)) }
                CtrlButton(onClick = onAccept) { Text(stringResource(R.string.invite_accept)) }
            }
        }
    }
}
