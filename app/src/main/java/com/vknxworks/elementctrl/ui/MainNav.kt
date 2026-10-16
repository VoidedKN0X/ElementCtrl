package com.vknxworks.elementctrl.ui

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import com.vknxworks.elementctrl.matrix.AccountService
import com.vknxworks.elementctrl.matrix.MatrixSession
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
    val joinTarget by DeepLinks.joinTarget.collectAsStateWithLifecycle()
    LaunchedEffect(joinTarget) {
        val (target, via) = joinTarget ?: return@LaunchedEffect
        DeepLinks.joinTarget.value = null
        val known = session.rooms.value.firstOrNull { it.roomId == target || it.canonicalAlias == target }
        if (known != null && !known.isInvite) showRoom(known.roomId)
        else runCatching { account.join(target, via) }.onSuccess { showRoom(it) }.onFailure { linkError = it.userMessage() }
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

    NavHost(nav, startDestination = "home") {
        composable("home") {
            val home: @Composable (Modifier) -> Unit = { modifier ->
                androidx.compose.foundation.layout.Box(modifier) {
                    HomeScreen(
                        session,
                        onOpenRoom = { showRoom(it) },
                        onNewChat = { nav.go("newchat") },
                        onSettings = { nav.go("settings") },
                        onSecurity = { nav.go("security") },
                        selectedRoomId = if (wide) paneRoom else null,
                    )
                }
            }
            if (!wide) {
                home(Modifier.fillMaxSize())
            } else {
                Row(Modifier.fillMaxSize()) {
                    home(Modifier.width(380.dp).fillMaxHeight())
                    VerticalDivider()
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxHeight()) {
                        val id = paneRoom
                        if (id == null) {
                            EmptyState(
                                Icons.AutoMirrored.Filled.Chat,
                                "Pick a chat",
                                "Choose a conversation on the left to open it here.",
                                Modifier.align(androidx.compose.ui.Alignment.Center),
                            )
                        } else {
                            key(id) {
                                // Each chat in the pane gets its own view-model store, released when another is opened.
                                ScopedViewModels {
                                    RoomScreen(
                                        session, id, threadRootId = null,
                                        onBack = { paneRoom = null },
                                        onDetails = { nav.go("details/${enc(id)}") },
                                        onOpenThread = { root -> nav.go("thread/${enc(id)}/${enc(root)}") },
                                        onOpenUser = { nav.go("user/${enc(it)}?room=${enc(id)}") },
                                        onOpenImage = { media = it; nav.go("media") },
                                        onOpenRoom = { paneRoom = it },
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
                onOpenImage = { media = it; nav.go("media") },
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
        composable("devices") { DevicesScreen(session, onBack = { nav.back() }) }
        composable("thread/{roomId}/{rootId}") { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            val rootId = entry.arguments?.getString("rootId").orEmpty()
            RoomScreen(
                session, roomId, threadRootId = rootId,
                onBack = { nav.back() },
                onDetails = {},
                onOpenThread = {},
                onOpenUser = { nav.go("user/${enc(it)}?room=${enc(roomId)}") },
                onOpenImage = { media = it; nav.go("media") },
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
                onLeft = { paneRoom = null; nav.popBackStack("home", inclusive = false) },
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
                onExplore = { nav.go("explore") },
            )
        }
        composable("createroom") {
            CreateRoomScreen(session, onBack = { nav.back() }, onCreated = { id ->
                if (wide) showRoom(id) else nav.navigate("room/${enc(id)}") { popUpTo("home"); launchSingleTop = true }
            })
        }
        composable("explore") {
            ExploreScreen(session, onBack = { nav.back() }, onOpenRoom = { id ->
                if (wide) showRoom(id) else nav.navigate("room/${enc(id)}") { popUpTo("home"); launchSingleTop = true }
            })
        }
        composable("settings") {
            SettingsScreen(
                session,
                onBack = { nav.back() },
                onSecurity = { nav.go("security") },
                onOpenUser = { nav.go("user/${enc(it)}") },
                onDevices = { nav.go("devices") },
            )
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
            val m = media
            if (m != null) MediaViewer(session, m, onBack = { nav.back() }) else LaunchedEffect(Unit) { nav.close("media") }
        }
    }

    linkError?.let {
        AlertDialog(
            onDismissRequest = { linkError = null },
            title = { Text("Couldn't open link") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { linkError = null }) { Text("OK") } },
        )
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
