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
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.SyncServiceState
import org.matrix.rustcomponents.sdk.VerificationState

enum class RoomFilter(val label: String) { ALL("All"), UNREAD("Unread"), PEOPLE("People"), ROOMS("Rooms"), FAVOURITES("Favourites"), INVITES("Invites") }

@Composable
fun HomeScreen(
    session: MatrixSession,
    onOpenRoom: (String) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
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
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search chats") },
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
                    navigationIcon = { IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Default.Close, "Close search") } },
                )
            } else {
                TopAppBar(
                    title = { Text("Chats", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onSettings) {
                            Avatar(profile.displayName ?: session.userId, profile.avatarUrl, session.userId, size = 32.dp)
                        }
                    },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search") }
                    },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                icon = { Icon(Icons.Default.Edit, null) },
                text = { Text("New chat") },
                expanded = listState.firstVisibleItemIndex == 0,
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            AnimatedVisibility(sync == SyncServiceState.OFFLINE || sync == SyncServiceState.ERROR) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (sync == SyncServiceState.OFFLINE) "Offline — showing saved messages" else "Connection problem, retrying…",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (!loaded && rooms.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            val sharing by com.vknxworks.elementctrl.ui.DeepLinks.share.collectAsStateWithLifecycle()
            if (sharing != null) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Choose a chat to share to", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    IconButton(onClick = { com.vknxworks.elementctrl.ui.DeepLinks.share.value = null }) { Icon(Icons.Default.Close, "Cancel sharing") }
                }
            }

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                if (verification == VerificationState.UNVERIFIED) {
                    item("verify") {
                        Banner(
                            icon = Icons.Default.VerifiedUser,
                            title = "Confirm it's you",
                            body = "Verify this device to read your encrypted message history and let others trust it.",
                            action = "Verify",
                            onClick = onSecurity,
                        )
                    }
                } else if (verification == VerificationState.VERIFIED && (recovery == RecoveryState.DISABLED || recovery == RecoveryState.INCOMPLETE)) {
                    item("recovery") {
                        Banner(
                            icon = Icons.Default.Security,
                            title = if (recovery == RecoveryState.INCOMPLETE) "Finish setting up recovery" else "Set up recovery",
                            body = "Keep a recovery key so you never lose your encrypted messages if you lose your devices.",
                            action = "Set up",
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
                                label = { Text(if (f == RoomFilter.INVITES) "Invites ($inviteCount)" else f.label) },
                            )
                        }
                    }
                }
                if (loaded && visible.isEmpty()) {
                    item("empty") {
                        EmptyState(
                            Icons.AutoMirrored.Filled.Chat,
                            if (query.isNotBlank() || filter != RoomFilter.ALL) "Nothing here" else "No chats yet",
                            if (query.isNotBlank() || filter != RoomFilter.ALL) "Try another filter or search." else "Start a chat with someone or join a room.",
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
                SettingRow(Icons.Default.MarkChatRead, "Mark as read") {
                    withRoom { it.markAsRead(ReceiptType.READ); it.setUnreadFlag(false) }
                }
            } else {
                SettingRow(Icons.Default.MarkChatUnread, "Mark as unread") { withRoom { it.setUnreadFlag(true) } }
            }
            SettingRow(
                if (room.isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (room.isFavourite) "Remove from favourites" else "Add to favourites",
            ) { withRoom { it.setIsFavourite(!room.isFavourite, null) } }
            SettingRow(Icons.Default.SouthEast, if (room.isLowPriority) "Remove low priority" else "Low priority") {
                withRoom { it.setIsLowPriority(!room.isLowPriority, null) }
            }
            SettingRow(Icons.AutoMirrored.Filled.ExitToApp, "Leave", tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) {
                leaving = room; menuFor = null
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    leaving?.let { room ->
        ConfirmDialog(
            title = "Leave ${room.name}?",
            text = if (room.isDm) "You'll stop receiving messages from this chat." else "You'll need an invite to rejoin if the room is private.",
            confirm = "Leave",
            destructive = true,
            onDismiss = { leaving = null },
            onConfirm = { scope.launch { runCatching { session.getRoom(room.roomId)?.use { it.leave() } } } },
        )
    }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String, onClick: () -> Unit) {
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
            Button(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun RoomRow(room: RoomSummary, draft: String?, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
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
                    Icon(Icons.Default.Lock, "Encrypted", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                room.lastTimestamp?.let {
                    Text(
                        shortTime(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (room.hasUnread && !room.isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (room.lastFailed) {
                    Icon(Icons.Default.ErrorOutline, "Not sent", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    if (draft != null) {
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)) { append("Draft: ") }
                            append(draft)
                        }
                    } else androidx.compose.ui.text.AnnotatedString(buildString {
                        if (!room.isDm && room.lastSender != null) append(room.lastSender).append(": ")
                        else if (room.isDm && room.lastIsOwn && room.lastSender != null) append("You: ")
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
                    Icon(Icons.Default.NotificationsOff, "Muted", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (room.isFavourite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Favorite, "Favourite", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    "${room.inviterName ?: "Someone"} invited you" + if (room.isDm) " to chat" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            UnreadBadge(0, highlight = true, muted = false)
        }
        Row(Modifier.padding(start = 66.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDecline) { Text("Decline") }
            Button(onClick = onAccept) { Text("Accept") }
        }
    }
}
