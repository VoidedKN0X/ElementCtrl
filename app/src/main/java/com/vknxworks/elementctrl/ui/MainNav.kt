package com.vknxworks.elementctrl.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.vknxworks.elementctrl.R
import kotlinx.coroutines.launch
import com.vknxworks.elementctrl.ui.settings.YouScreen
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vknxworks.elementctrl.ui.room.SearchScreen
import com.vknxworks.elementctrl.ui.settings.DevicesScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.matrix.AccountService
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.RoomLinkPreview
import com.vknxworks.elementctrl.matrix.VerifyStep
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.ui.home.HomeScreen
import com.vknxworks.elementctrl.ui.newchat.CreateRoomScreen
import com.vknxworks.elementctrl.ui.newchat.ExploreScreen
import com.vknxworks.elementctrl.ui.newchat.NewChatScreen
import com.vknxworks.elementctrl.ui.room.MediaRequest
import com.vknxworks.elementctrl.ui.room.MediaViewer
import com.vknxworks.elementctrl.ui.room.MembersScreen
import com.vknxworks.elementctrl.ui.room.RoomDetailsScreen
import com.vknxworks.elementctrl.ui.room.RoomScreen
import com.vknxworks.elementctrl.ui.room.UserScreen
import com.vknxworks.elementctrl.ui.settings.SecurityScreen
import com.vknxworks.elementctrl.ui.settings.SettingsScreen
import com.vknxworks.elementctrl.ui.settings.VerifyScreen

private fun enc(s: String) = Uri.encode(s)

private fun NavHostController.openRoom(roomId: String, replaceCurrent: Boolean = false) {
    navigate("room/${enc(roomId)}") {
        // Never replace the chat list itself: with it gone, going back left an empty, black screen.
        val current = currentDestination
        if (replaceCurrent && current != null && current.route != "home") popUpTo(current.id) { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * Back from a screen the user is looking at. Repeat taps while a screen change is still animating
 * are ignored, and the chat list at the bottom is never popped (that emptied the screen stack and
 * left a black screen until the app was restarted).
 */
private fun NavHostController.back() {
    val current = currentBackStackEntry ?: return
    if (previousBackStackEntry == null) return
    if (!current.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
    popBackStack()
}

/** Closes [route] if it's on top, for screens that finish on their own (not from a tap). */
private fun NavHostController.close(route: String) {
    if (previousBackStackEntry != null && currentBackStackEntry?.destination?.route == route) popBackStack()
}

/** Opens a screen from a tap, ignoring taps on a screen that's already on its way out or not shown yet. */
private fun NavHostController.go(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == false) return
    navigate(route)
}

/** All screens after sign-in. */
@Composable
fun MainNav(session: MatrixSession) {
    val nav = rememberNavController()
    var media by remember { mutableStateOf<MediaRequest?>(null) }
    val account = remember(session) { AccountService(session) }
    var linkError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()

    // Tablets and unfolded foldables show the chat list and the open chat side by side.
    val wide = isWideLayout()
    var paneRoom by rememberSaveable { mutableStateOf<String?>(null) }
    fun showRoom(id: String) {
        if (wide) {
            paneRoom = id
            if (nav.currentBackStackEntry?.destination?.route != "home") nav.popBackStack("home", inclusive = false)
        } else nav.openRoom(id)
    }
    LaunchedEffect(wide) {
        val id = paneRoom
        if (!wide && id != null) { paneRoom = null; nav.openRoom(id) }
        // Turned or unfolded while a chat was open full screen: move it into the pane.
        val entry = nav.currentBackStackEntry
        if (wide && entry?.destination?.route == "room/{roomId}?focus={focus}" && entry.arguments?.getString("focus") == null) {
            entry.arguments?.getString("roomId")?.let { rid -> paneRoom = rid; nav.popBackStack("home", inclusive = false) }
        }
    }

    // Notification taps and matrix.to links.
    val openRoom by DeepLinks.openRoom.collectAsStateWithLifecycle()
    LaunchedEffect(openRoom) {
        openRoom?.let { DeepLinks.openRoom.value = null; showRoom(it) }
    }
    // A link to a room never joins on its own: rooms you're in (or invited to, where you accept or decline)
    // just open, anything else asks first. The link stays pending until you answer, so one that arrives while
    // the app is locked still asks after unlocking.
    val joinTarget by DeepLinks.joinTarget.collectAsStateWithLifecycle()
    var askJoin by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    LaunchedEffect(joinTarget) {
        val pending = joinTarget ?: return@LaunchedEffect
        val target = pending.first
        val known = session.rooms.value.firstOrNull { it.roomId == target || it.canonicalAlias == target }
        if (known != null) {
            DeepLinks.joinTarget.value = null
            showRoom(known.roomId)
        } else askJoin = pending
    }
    askJoin?.let { pending ->
        JoinLinkDialog(
            target = pending.first,
            via = pending.second,
            account = account,
            onDismiss = {
                askJoin = null
                DeepLinks.joinTarget.value = null
            },
            onJoin = {
                askJoin = null
                DeepLinks.joinTarget.value = null
                scope.launch {
                    runCatching { account.join(pending.first, pending.second) }
                        .onSuccess { showRoom(it) }
                        .onFailure { linkError = it.userMessage() }
                }
            },
        )
    }
    val openUser by DeepLinks.openUser.collectAsStateWithLifecycle()
    LaunchedEffect(openUser) {
        openUser?.let { DeepLinks.openUser.value = null; nav.navigate("user/${enc(it)}") }
    }

    // Someone asked to verify with us: jump to the verification screen.
    val step by session.verificationService.step.collectAsStateWithLifecycle()
    LaunchedEffect(step) {
        if (step is VerifyStep.Incoming && backStack?.destination?.route?.startsWith("verify") != true) {
            nav.navigate("verify?start=false")
        }
    }

    // The main sections, on the floating glass tab bar (phones) or a side rail (tablets), as in the other Ctrl apps.
    val route = backStack?.destination?.route
    val tabIndex = MainTabs.indexOfFirst { it.route == route }
    fun selectTab(i: Int) {
        val target = MainTabs[i].route
        if (route == target) return
        nav.navigate(target) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val host: @Composable () -> Unit = { MainHost(nav, session, wide, paneRoom, { paneRoom = it }, { showRoom(it) }, { media = it }, { media }, ::selectTab) }
    if (wide) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxHeight().statusBarsPadding(),
            ) {
                Spacer(Modifier.weight(1f))
                MainTabs.forEachIndexed { i, tab ->
                    val selected = i == tabIndex
                    NavigationRailItem(
                        selected = selected,
                        onClick = { selectTab(i) },
                        icon = { Icon(tab.item.icon, contentDescription = stringResource(tab.item.label)) },
                        label = { Text(stringResource(tab.item.label)) },
                        colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                            selectedIconColor = CtrlColors.OnAccent,
                            selectedTextColor = CtrlColors.Text,
                            indicatorColor = CtrlColors.Accent,
                            unselectedIconColor = CtrlColors.Muted,
                            unselectedTextColor = CtrlColors.Muted,
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxHeight()) { host() }
        }
    } else {
        PhoneShell(showTabs = tabIndex >= 0, selectedIndex = tabIndex, onSelect = ::selectTab, content = host)
    }

    linkError?.let {
        com.vknxworks.elementctrl.ui.components.CtrlDialog(
            onDismissRequest = { linkError = null },
            title = { Text(stringResource(R.string.link_error_title)) },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { linkError = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

/** "Join #room?" for a room from a link: what the server can show about it, with Join and Cancel. */
@Composable
private fun JoinLinkDialog(target: String, via: List<String>, account: AccountService, onDismiss: () -> Unit, onJoin: () -> Unit) {
    var loading by remember(target) { mutableStateOf(true) }
    var preview by remember(target) { mutableStateOf<RoomLinkPreview?>(null) }
    LaunchedEffect(target) {
        preview = account.preview(target, via)
        loading = false
    }
    val p = preview
    val title = p?.name ?: p?.alias ?: target
    com.vknxworks.elementctrl.ui.components.CtrlDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.join_link_title, title)) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                when {
                    loading -> Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    p == null -> Text(stringResource(R.string.join_link_no_preview))
                    else -> {
                        p.topic?.let {
                            Text(it, maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Text(
                            androidx.compose.ui.res.pluralStringResource(R.plurals.members_count, p.members.toInt().coerceAtLeast(0), p.members.toInt().coerceAtLeast(0)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // The address from the link, so a misleading room name can be checked.
                Text(
                    p?.alias ?: target,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onJoin) { Text(stringResource(R.string.join_link_join)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private data class MainTab(val route: String, val item: TabItem)

private val MainTabs = listOf(
    MainTab("home", TabItem(R.string.tab_chats, Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Outlined.Chat)),
    MainTab("explore", TabItem(R.string.tab_explore, Icons.Outlined.Explore, Icons.Outlined.Explore)),
)

/**
 * Phones: the main tabs run edge to edge and scroll under the floating glass tab bar; pushed screens (a chat,
 * the You page, settings) get the full height and the bar slides away.
 */
@Composable
private fun PhoneShell(showTabs: Boolean, selectedIndex: Int, onSelect: (Int) -> Unit, content: @Composable () -> Unit) {
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }
    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().hazeSource(hazeState)) { content() }
            androidx.compose.foundation.layout.Column(
                Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = showTabs,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    GlassTabBar(items = MainTabs.map { it.item }, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

/** Every screen after sign-in. */
@Composable
private fun MainHost(
    nav: NavHostController,
    session: MatrixSession,
    wide: Boolean,
    paneRoom: String?,
    setPaneRoom: (String?) -> Unit,
    showRoom: (String) -> Unit,
    setMedia: (MediaRequest) -> Unit,
    media: () -> MediaRequest?,
    selectTab: (Int) -> Unit,
) {
    NavHost(nav, startDestination = "home") {
        composable("home") {
            val home: @Composable (Modifier) -> Unit = { modifier ->
                androidx.compose.foundation.layout.Box(modifier) {
                    HomeScreen(
                        session,
                        onOpenRoom = { showRoom(it) },
                        onNewChat = { nav.go("newchat") },
                        onProfile = { nav.go("you") },
                        onSecurity = { nav.go("security") },
                        selectedRoomId = if (wide) paneRoom else null,
                    )
                }
            }
            if (!wide) {
                home(Modifier.fillMaxSize())
            } else {
                Row(Modifier.fillMaxSize()) {
                    home(Modifier.width(if (LocalConfiguration.current.screenWidthDp >= 840) 380.dp else 320.dp).fillMaxHeight())
                    VerticalDivider(color = CtrlColors.Divider)
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxHeight()) {
                        val id = paneRoom
                        if (id == null) {
                            EmptyState(
                                Icons.AutoMirrored.Outlined.Chat,
                                stringResource(R.string.chats_pick),
                                stringResource(R.string.chats_pick_body),
                                Modifier.align(androidx.compose.ui.Alignment.Center),
                            )
                        } else {
                            key(id) {
                                // Each chat in the pane gets its own view-model store, released when another is opened.
                                ScopedViewModels {
                                    RoomScreen(
                                        session, id, threadRootId = null,
                                        onBack = { setPaneRoom(null) },
                                        onDetails = { nav.go("details/${enc(id)}") },
                                        onOpenThread = { root -> nav.go("thread/${enc(id)}/${enc(root)}") },
                                        onOpenUser = { nav.go("user/${enc(it)}?room=${enc(id)}") },
                                        onOpenImage = { setMedia(it); nav.go("media") },
                                        onOpenRoom = { setPaneRoom(it) },
                                        onSecurity = { nav.go("security") },
                                        onSearch = { nav.go("search/${enc(id)}") },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composable(
            "room/{roomId}?focus={focus}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            val focus = entry.arguments?.getString("focus")
            RoomScreen(
                session, roomId, threadRootId = null,
                onBack = { nav.back() },
                onDetails = { nav.go("details/${enc(roomId)}") },
                onOpenThread = { root -> nav.go("thread/${enc(roomId)}/${enc(root)}") },
                onOpenUser = { nav.go("user/${enc(it)}?room=${enc(roomId)}") },
                onOpenImage = { setMedia(it); nav.go("media") },
                onOpenRoom = { nav.openRoom(it, replaceCurrent = true) },
                onSecurity = { nav.go("security") },
                onSearch = { nav.go("search/${enc(roomId)}") },
                focusEventId = focus,
                onJumpToLatest = { nav.openRoom(roomId, replaceCurrent = true) },
            )
        }
        composable("search/{roomId}") { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            SearchScreen(
                session, roomId, session.rooms.value.firstOrNull { it.roomId == roomId }?.name,
                onBack = { nav.back() },
                onOpen = { eventId -> nav.go("room/${enc(roomId)}?focus=${enc(eventId)}") },
            )
        }
        composable("devices") { DevicesScreen(session, onBack = { nav.back() }, onLinkDevice = { nav.go("linkdevice") }) }
        composable("linkdevice") { com.vknxworks.elementctrl.ui.settings.LinkDeviceScreen(session, onBack = { nav.back() }) }
        composable("thread/{roomId}/{rootId}") { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            val rootId = entry.arguments?.getString("rootId").orEmpty()
            RoomScreen(
                session, roomId, threadRootId = rootId,
                onBack = { nav.back() },
                onDetails = {},
                onOpenThread = {},
                onOpenUser = { nav.go("user/${enc(it)}?room=${enc(roomId)}") },
                onOpenImage = { setMedia(it); nav.go("media") },
                onOpenRoom = { nav.openRoom(it) },
                onSecurity = { nav.go("security") },
                onSearch = { nav.go("search/${enc(roomId)}") },
            )
        }
        composable("details/{roomId}") { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            RoomDetailsScreen(
                session, roomId,
                onBack = { nav.back() },
                onMembers = { nav.go("members/${enc(roomId)}") },
                onOpenUser = { nav.go("user/${enc(it)}?room=${enc(roomId)}") },
                onLeft = { setPaneRoom(null); nav.popBackStack("home", inclusive = false) },
                onMedia = { nav.go("gallery/${enc(roomId)}") },
            )
        }
        composable("gallery/{roomId}") { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            com.vknxworks.elementctrl.ui.room.MediaGalleryScreen(
                session, roomId,
                onBack = { nav.back() },
                onOpenImage = { setMedia(it); nav.go("media") },
            )
        }
        composable("members/{roomId}") { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            MembersScreen(session, roomId, onBack = { nav.back() }, onOpenUser = { nav.go("user/${enc(it)}?room=${enc(roomId)}") })
        }
        composable(
            "user/{userId}?room={room}",
            arguments = listOf(navArgument("room") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val userId = entry.arguments?.getString("userId").orEmpty()
            UserScreen(
                session, userId, entry.arguments?.getString("room"),
                onBack = { nav.back() },
                onOpenRoom = { if (wide) showRoom(it) else nav.openRoom(it) },
                onVerify = { nav.go("verify?user=${enc(it)}&start=true") },
            )
        }
        composable("newchat") {
            NewChatScreen(
                session,
                onBack = { nav.back() },
                onOpenRoom = { if (wide) showRoom(it) else nav.openRoom(it, replaceCurrent = true) },
                onCreateRoom = { nav.go("createroom") },
                onExplore = { selectTab(1) },
            )
        }
        composable("createroom") {
            CreateRoomScreen(session, onBack = { nav.back() }, onCreated = { id ->
                if (wide) showRoom(id) else nav.navigate("room/${enc(id)}") { popUpTo("home"); launchSingleTop = true }
            })
        }
        composable("explore") {
            ExploreScreen(session, onBack = { nav.back() }, onProfile = { nav.go("you") }, onOpenRoom = { id ->
                if (wide) {
                    showRoom(id)
                    selectTab(0)
                } else nav.navigate("room/${enc(id)}") { launchSingleTop = true }
            })
        }
        composable("you") {
            YouScreen(
                session,
                onBack = { nav.back() },
                onSettings = { nav.go("settings") },
                onSecurity = { nav.go("security") },
                onDevices = { nav.go("devices") },
                onOpenUser = { nav.go("user/${enc(it)}") },
            )
        }
        composable("settings") {
            SettingsScreen(session, onBack = { nav.back() }, extraGroups = { com.vknxworks.elementctrl.call.CallSettingsGroup(session) })
        }
        composable("security") {
            SecurityScreen(session, onBack = { nav.back() }, onVerifyDevice = { nav.go("verify?start=true") })
        }
        composable(
            "verify?user={user}&start={start}",
            arguments = listOf(
                navArgument("user") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("start") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            VerifyScreen(
                session,
                otherUserIdArg = entry.arguments?.getString("user"),
                startOutgoing = entry.arguments?.getBoolean("start") == true,
                onDone = { nav.close("verify?user={user}&start={start}") },
            )
        }
        composable("media") {
            val m = media()
            if (m != null) MediaViewer(session, m, onBack = { nav.back() }) else LaunchedEffect(Unit) { nav.close("media") }
        }
    }

}

/** Two panes from 840 dp wide (tablets, unfolded foldables), or 600 dp in landscape. */
@Composable
fun isWideLayout(): Boolean {
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    return c.screenWidthDp >= 840 || (c.screenWidthDp >= 600 && c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
}

/** Gives its content a private view-model store that is cleared when the content leaves the screen. */
@Composable
private fun ScopedViewModels(content: @Composable () -> Unit) {
    val owner = remember {
        object : androidx.lifecycle.ViewModelStoreOwner {
            override val viewModelStore = androidx.lifecycle.ViewModelStore()
        }
    }
    androidx.compose.runtime.DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides owner) { content() }
}
