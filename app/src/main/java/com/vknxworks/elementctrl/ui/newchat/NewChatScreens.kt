package com.vknxworks.elementctrl.ui.newchat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.matrix.AccountService
import com.vknxworks.elementctrl.matrix.DirectorySearch
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.ui.Avatar
import com.vknxworks.elementctrl.ui.SectionHeader
import com.vknxworks.elementctrl.ui.SettingRow
import com.vknxworks.elementctrl.ui.TextInputDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.UserProfile

/** Start a direct chat, or go to create/join/explore rooms. */
@Composable
fun NewChatScreen(
    session: MatrixSession,
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onExplore: () -> Unit,
) {
    val account = remember { AccountService(session) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<String?>(null) }
    var joinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        results = runCatching { account.searchUsers(query.trim()) }.getOrDefault(emptyList())
        searching = false
    }

    fun startDm(userId: String) {
        opening = userId
        scope.launch {
            runCatching { account.startDm(userId) }
                .onSuccess { onOpenRoom(it) }
                .onFailure { snackbar.showSnackbar(it.userMessage()) }
            opening = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("New chat") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).imePadding()) {
            item {
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Search by name or @user:server") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            if (query.isBlank()) {
                item { SettingRow(Icons.Default.GroupAdd, "Create a room", "A group chat for a team, friends or a topic", onClick = onCreateRoom) }
                item { SettingRow(Icons.Default.Tag, "Join with an address", "#room:server or a room ID") { joinDialog = true } }
                item { SettingRow(Icons.Default.Explore, "Explore public rooms", "Browse the room directory", onClick = onExplore) }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item {
                    Text(
                        "Search for someone to start an encrypted direct chat. Use their full Matrix ID (like @alice:matrix.org) if they're on another server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                items(results, key = { it.userId }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = opening == null) { startDm(p.userId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(p.displayName ?: p.userId, p.avatarUrl, p.userId, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.displayName ?: p.userId, style = MaterialTheme.typography.bodyLarge)
                            Text(p.userId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (opening == p.userId) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                if (!searching && results.isEmpty() && query.length >= 2) {
                    item { Text("No one found. Try their full Matrix ID.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    if (joinDialog) {
        TextInputDialog("Join a room", label = "#room:server.org", confirm = "Join", onDismiss = { joinDialog = false }) { address ->
            scope.launch {
                runCatching { account.join(address) }
                    .onSuccess { onOpenRoom(it) }
                    .onFailure { snackbar.showSnackbar(it.userMessage()) }
            }
        }
    }
}

@Composable
fun CreateRoomScreen(session: MatrixSession, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val account = remember { AccountService(session) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var name by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    var encrypted by remember { mutableStateOf(true) }
    var alias by remember { mutableStateOf("") }
    val invites = remember { mutableStateListOf<String>() }
    var inviteDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Create room") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(enabled = name.isNotBlank() && !busy, onClick = {
                        busy = true
                        scope.launch {
                            runCatching { account.createRoom(name.trim(), topic, isPublic, encrypted, alias.trim().removePrefix("#").substringBefore(':'), invites.toList()) }
                                .onSuccess { onCreated(it) }
                                .onFailure { snackbar.showSnackbar(it.userMessage()) }
                            busy = false
                        }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Create") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Room name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(topic, { topic = it }, label = { Text("Topic (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Public room", style = MaterialTheme.typography.bodyLarge)
                    Text("Anyone can find and join. Public rooms aren't encrypted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(isPublic, { isPublic = it })
            }
            if (isPublic) {
                OutlinedTextField(
                    alias, { alias = it },
                    label = { Text("Address") },
                    prefix = { Text("#") },
                    suffix = { Text(":" + session.client.userIdServerName()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("End-to-end encryption", style = MaterialTheme.typography.bodyLarge)
                        Text("Recommended. Can't be turned off later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(encrypted, { encrypted = it })
                }
            }
            SectionHeader("Invite people", Modifier.padding(0.dp))
            invites.forEach { id ->
                InputChip(selected = false, onClick = { invites.remove(id) }, label = { Text(id, maxLines = 1, overflow = TextOverflow.Ellipsis) }, trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) })
            }
            TextButton(onClick = { inviteDialog = true }) { Text("Add people") }
        }
    }
    if (inviteDialog) com.vknxworks.elementctrl.ui.room.InviteDialog(session, onDismiss = { inviteDialog = false }) { if (it !in invites) invites += it }
}

/** Browse and join public rooms on your server or another one. */
@Composable
fun ExploreScreen(session: MatrixSession, onBack: () -> Unit, onOpenRoom: (String) -> Unit) {
    val search = remember { DirectorySearch(session) }
    DisposableEffect(Unit) { onDispose { search.close() } }
    val account = remember { AccountService(session) }
    val results by search.results.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val atEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= results.size - 3 } }

    LaunchedEffect(query, server) {
        delay(400)
        runCatching { search.query(query, server) }.onFailure { snackbar.showSnackbar(it.userMessage()) }
    }
    LaunchedEffect(atEnd, results.size) { if (atEnd && results.isNotEmpty()) search.more() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Explore rooms") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).imePadding(), state = listState) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, placeholder = { Text("Search rooms") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(server, { server = it }, placeholder = { Text("Server (blank for yours), e.g. matrix.org") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            items(results, key = { it.roomId }) { r ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = joining == null) {
                        joining = r.roomId
                        scope.launch {
                            val via = listOfNotNull(server.takeIf { it.isNotBlank() })
                            runCatching { account.join(r.alias ?: r.roomId, via) }
                                .onSuccess { onOpenRoom(it) }
                                .onFailure { snackbar.showSnackbar(it.userMessage()) }
                            joining = null
                        }
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(r.name ?: r.alias ?: r.roomId, r.avatarUrl, r.roomId, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name ?: r.alias ?: r.roomId, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(r.alias, "${r.joinedMembers} members").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        r.topic?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                    if (joining == r.roomId) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}
