package com.vknxworks.elementctrl.ui

import android.net.Uri
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
import com.vknxworks.elementctrl.ui.settings.ConfirmIdentityScreen
import com.vknxworks.elementctrl.ui.settings.SecurityScreen
import com.vknxworks.elementctrl.ui.settings.SettingsScreen
import com.vknxworks.elementctrl.ui.settings.VerifyScreen

private fun enc(s: String) = Uri.encode(s)

private fun NavHostController.openRoom(roomId: String, replaceCurrent: Boolean = false) {
    navigate("room/${enc(roomId)}") {
        if (replaceCurrent) currentDestination?.id?.let { popUpTo(it) { inclusive = true } }
        launchSingleTop = true
    }
}

/** All screens after sign-in. */
@Composable
fun MainNav(session: MatrixSession) {
    val nav = rememberNavController()
    var media by remember { mutableStateOf<MediaRequest?>(null) }
    val account = remember(session) { AccountService(session) }
    var linkError by remember { mutableStateOf<String?>(null) }
    val backStack by nav.currentBackStackEntryAsState()

    // Notification taps and matrix.to links.
    val openRoom by DeepLinks.openRoom.collectAsStateWithLifecycle()
    LaunchedEffect(openRoom) {
        openRoom?.let { DeepLinks.openRoom.value = null; nav.openRoom(it) }
    }
    val joinTarget by DeepLinks.joinTarget.collectAsStateWithLifecycle()
    LaunchedEffect(joinTarget) {
        val (target, via) = joinTarget ?: return@LaunchedEffect
        DeepLinks.joinTarget.value = null
        val known = session.rooms.value.firstOrNull { it.roomId == target || it.canonicalAlias == target }
        if (known != null && !known.isInvite) nav.openRoom(known.roomId)
        else runCatching { account.join(target, via) }.onSuccess { nav.openRoom(it) }.onFailure { linkError = it.userMessage() }
    }
    val openUser by DeepLinks.openUser.collectAsStateWithLifecycle()
    LaunchedEffect(openUser) {
        openUser?.let { DeepLinks.openUser.value = null; nav.navigate("user/${enc(it)}") }
    }

    // Like Element X: an unverified login is asked to confirm its identity before anything else, because
    // until then it can't get message keys. Skipping only lasts until the app is restarted.
    val verification by session.verification.collectAsStateWithLifecycle()
    var confirmSkipped by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(verification, confirmSkipped) {
        if (verification != org.matrix.rustcomponents.sdk.VerificationState.UNVERIFIED || confirmSkipped) return@LaunchedEffect
        kotlinx.coroutines.delay(1500) // let the state settle after start-up
        if (session.verification.value == org.matrix.rustcomponents.sdk.VerificationState.UNVERIFIED &&
            nav.currentBackStackEntry?.destination?.route == "home"
        ) nav.navigate("confirm")
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
            HomeScreen(
                session,
                onOpenRoom = { nav.openRoom(it) },
                onNewChat = { nav.navigate("newchat") },
                onSettings = { nav.navigate("settings") },
                onSecurity = { nav.navigate("security") },
            )
        }
        composable("room/{roomId}", arguments = listOf(navArgument("roomId") { type = NavType.StringType })) { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            RoomScreen(
                session, roomId, threadRootId = null,
                onBack = { nav.popBackStack() },
                onDetails = { nav.navigate("details/${enc(roomId)}") },
                onOpenThread = { root -> nav.navigate("thread/${enc(roomId)}/${enc(root)}") },
                onOpenUser = { nav.navigate("user/${enc(it)}?room=${enc(roomId)}") },
                onOpenImage = { media = it; nav.navigate("media") },
                onOpenRoom = { nav.openRoom(it, replaceCurrent = true) },
                onSecurity = { nav.navigate("security") },
            )
        }
        composable("thread/{roomId}/{rootId}") { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            val rootId = entry.arguments?.getString("rootId").orEmpty()
            RoomScreen(
                session, roomId, threadRootId = rootId,
                onBack = { nav.popBackStack() },
                onDetails = {},
                onOpenThread = {},
                onOpenUser = { nav.navigate("user/${enc(it)}?room=${enc(roomId)}") },
                onOpenImage = { media = it; nav.navigate("media") },
                onOpenRoom = { nav.openRoom(it) },
                onSecurity = { nav.navigate("security") },
            )
        }
        composable("details/{roomId}") { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            RoomDetailsScreen(
                session, roomId,
                onBack = { nav.popBackStack() },
                onMembers = { nav.navigate("members/${enc(roomId)}") },
                onOpenUser = { nav.navigate("user/${enc(it)}?room=${enc(roomId)}") },
                onLeft = { nav.popBackStack("home", inclusive = false) },
            )
        }
        composable("members/{roomId}") { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            MembersScreen(session, roomId, onBack = { nav.popBackStack() }, onOpenUser = { nav.navigate("user/${enc(it)}?room=${enc(roomId)}") })
        }
        composable(
            "user/{userId}?room={room}",
            arguments = listOf(navArgument("room") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val userId = entry.arguments?.getString("userId").orEmpty()
            UserScreen(
                session, userId, entry.arguments?.getString("room"),
                onBack = { nav.popBackStack() },
                onOpenRoom = { nav.openRoom(it) },
                onVerify = { nav.navigate("verify?user=${enc(it)}&start=true") },
            )
        }
        composable("newchat") {
            NewChatScreen(
                session,
                onBack = { nav.popBackStack() },
                onOpenRoom = { nav.openRoom(it, replaceCurrent = true) },
                onCreateRoom = { nav.navigate("createroom") },
                onExplore = { nav.navigate("explore") },
            )
        }
        composable("createroom") {
            CreateRoomScreen(session, onBack = { nav.popBackStack() }, onCreated = { id ->
                nav.navigate("room/${enc(id)}") { popUpTo("home") }
            })
        }
        composable("explore") {
            ExploreScreen(session, onBack = { nav.popBackStack() }, onOpenRoom = { id -> nav.navigate("room/${enc(id)}") { popUpTo("home") } })
        }
        composable("settings") {
            SettingsScreen(session, onBack = { nav.popBackStack() }, onSecurity = { nav.navigate("security") }, onOpenUser = { nav.navigate("user/${enc(it)}") })
        }
        composable("confirm") {
            ConfirmIdentityScreen(
                session,
                onUseDevice = { nav.navigate("verify?start=true") },
                onCantConfirm = { nav.navigate("security") },
                onDone = { confirmSkipped = true; nav.popBackStack("home", inclusive = false) },
            )
        }
        composable("security") {
            SecurityScreen(session, onBack = { nav.popBackStack() }, onVerifyDevice = { nav.navigate("verify?start=true") })
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
                onDone = { nav.popBackStack() },
            )
        }
        composable("media") {
            val m = media
            if (m != null) MediaViewer(session, m, onBack = { nav.popBackStack() }) else LaunchedEffect(Unit) { nav.popBackStack() }
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
