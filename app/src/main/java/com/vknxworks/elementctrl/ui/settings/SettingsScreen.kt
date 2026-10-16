package com.vknxworks.elementctrl.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.BuildConfig
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.data.ThemeMode
import com.vknxworks.elementctrl.matrix.AccountService
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.ui.Avatar
import com.vknxworks.elementctrl.ui.ConfirmDialog
import com.vknxworks.elementctrl.ui.SectionHeader
import com.vknxworks.elementctrl.ui.SettingRow
import com.vknxworks.elementctrl.ui.TextInputDialog
import com.vknxworks.elementctrl.ui.copyText
import com.vknxworks.elementctrl.ui.openUrl
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.sdkGitSha

@Composable
fun SettingsScreen(session: MatrixSession, onBack: () -> Unit, onSecurity: () -> Unit, onOpenUser: (String) -> Unit) {
    val graph = ElementCtrlApp.graph
    val prefs = graph.prefs
    val account = remember { AccountService(session) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val profile by session.profile.collectAsStateWithLifecycle()
    val recovery by session.recovery.collectAsStateWithLifecycle()
    val theme by prefs.theme.collectAsStateWithLifecycle()
    val dynamic by prefs.dynamicColor.collectAsStateWithLifecycle()
    val notifications by prefs.notifications.collectAsStateWithLifecycle()
    val sendOnEnter by prefs.sendOnEnter.collectAsStateWithLifecycle()
    val showState by prefs.showStateEvents.collectAsStateWithLifecycle()
    val bubbleStyle by prefs.bubbleStyle.collectAsStateWithLifecycle()
    var bubbleDialog by remember { mutableStateOf(false) }

    var editName by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var modeDialog by remember { mutableStateOf<Boolean?>(null) } // true = DMs, false = groups
    var groupMode by remember { mutableStateOf<RoomNotificationMode?>(null) }
    var dmMode by remember { mutableStateOf<RoomNotificationMode?>(null) }
    var mentions by remember { mutableStateOf(true) }
    var invites by remember { mutableStateOf(true) }
    var accountUrl by remember { mutableStateOf<String?>(null) }
    var ignored by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        accountUrl = account.accountManagementUrl()
        runCatching { groupMode = account.defaultMode(false); dmMode = account.defaultMode(true) }
        runCatching { mentions = account.mentionsEnabled(); invites = account.invitesEnabled() }
        ignored = runCatching { account.ignoredUsers() }.getOrDefault(emptyList())
    }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { scope.launch { runCatching { account.setAvatar(context, it) }.onFailure { e -> snackbar.showSnackbar(e.userMessage()) } } }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.setNotifications(granted)
        if (!granted) scope.launch { snackbar.showSnackbar("Allow notifications for ElementCtrl in Android settings to get them.") }
    }

    fun modeLabel(m: RoomNotificationMode?) = when (m) {
        RoomNotificationMode.ALL_MESSAGES -> "All messages"
        RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY -> "Mentions and keywords"
        RoomNotificationMode.MUTE -> "Off"
        null -> "…"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(
                    profile.displayName ?: session.userId, profile.avatarUrl, session.userId, size = 72.dp,
                    modifier = Modifier.clickable { pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName ?: session.userId, style = MaterialTheme.typography.titleLarge)
                    Text(session.userId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editName = true }) { Icon(Icons.Default.Edit, "Edit name") }
            }
            HorizontalDivider()

            SectionHeader("Account")
            SettingRow(Icons.Default.Person, "Your profile", "How others see you") { onOpenUser(session.userId) }
            SettingRow(
                Icons.Default.Security, "Security & recovery",
                when (recovery) {
                    RecoveryState.ENABLED -> "Recovery is on"
                    RecoveryState.DISABLED -> "Set up recovery to protect your messages"
                    RecoveryState.INCOMPLETE -> "Enter your recovery key"
                    RecoveryState.UNKNOWN -> "Verification, recovery key, key backup"
                },
                onClick = onSecurity,
            )
            accountUrl?.let { url ->
                SettingRow(
                    Icons.AutoMirrored.Filled.OpenInNew, "Manage account",
                    "Password, email, signed-in devices",
                ) { openUrl(context, url) }
            }

            SectionHeader("Notifications")
            SettingRow(
                Icons.Default.Notifications, "Notifications on this device",
                "Checks for new messages about every 15 minutes while the app is closed",
                trailing = {
                    Switch(notifications, { on ->
                        if (on && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else prefs.setNotifications(on)
                    })
                },
            )
            SettingRow(Icons.Default.Group, "Group chats", modeLabel(groupMode)) { modeDialog = false }
            SettingRow(Icons.Default.ChatBubbleOutline, "Direct chats", modeLabel(dmMode)) { modeDialog = true }
            SettingRow(Icons.Default.AlternateEmail, "Mentions", "Notify when someone mentions you or @room", trailing = {
                Switch(mentions, { on -> mentions = on; scope.launch { runCatching { account.setMentionsEnabled(on) } } })
            })
            SettingRow(Icons.Default.MailOutline, "Invites", "Notify when you're invited to a room", trailing = {
                Switch(invites, { on -> invites = on; scope.launch { runCatching { account.setInvitesEnabled(on) } } })
            })

            SectionHeader("Appearance")
            SettingRow(Icons.Default.Brightness6, "Theme", when (theme) { ThemeMode.SYSTEM -> "Follow system"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }) { themeDialog = true }
            SettingRow(Icons.Default.ChatBubbleOutline, "Chat bubbles", bubbleStyle.label + " · " + bubbleStyle.description) { bubbleDialog = true }
            SettingRow(Icons.Default.Palette, "Material You colours", "Use your wallpaper's colours", trailing = { Switch(dynamic, prefs::setDynamicColor) })
            SettingRow(Icons.Default.Visibility, "Show room events", "Joins, leaves, renames and other changes", trailing = { Switch(showState, prefs::setShowStateEvents) })
            SettingRow(Icons.Default.KeyboardReturn, "Enter sends", "Press Enter to send instead of adding a new line", trailing = { Switch(sendOnEnter, prefs::setSendOnEnter) })

            if (ignored.isNotEmpty()) {
                SectionHeader("Ignored people")
                ignored.forEach { id ->
                    SettingRow(Icons.Default.Block, id, "Tap to unignore") {
                        scope.launch { runCatching { account.unignore(id) }.onSuccess { ignored = ignored - id } }
                    }
                }
            }

            SectionHeader("About")
            SettingRow(Icons.Default.Dns, "Homeserver", session.homeserver) { copyText(context, "Homeserver", session.homeserver) }
            SettingRow(Icons.Default.Info, "ElementCtrl ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", "Matrix Rust SDK ${runCatching { sdkGitSha().take(8) }.getOrDefault("")}")

            HorizontalDivider(Modifier.padding(top = 12.dp))
            SettingRow(Icons.AutoMirrored.Filled.Logout, if (signingOut) "Signing out…" else "Sign out", tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) {
                if (!signingOut) confirmSignOut = true
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (editName) TextInputDialog("Display name", initial = profile.displayName.orEmpty(), onDismiss = { editName = false }) { name ->
        scope.launch { runCatching { account.setDisplayName(name.trim()) }.onFailure { snackbar.showSnackbar(it.userMessage()) } }
    }

    if (bubbleDialog) BubbleStylePicker(bubbleStyle, prefs::setBubbleStyle) { bubbleDialog = false }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (m, label) ->
                        Row(Modifier.fillMaxWidth().selectable(theme == m) { prefs.setTheme(m); themeDialog = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(theme == m, null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("Close") } },
        )
    }

    modeDialog?.let { dms ->
        val current = if (dms) dmMode else groupMode
        AlertDialog(
            onDismissRequest = { modeDialog = null },
            title = { Text(if (dms) "Direct chats" else "Group chats") },
            text = {
                Column {
                    listOf(RoomNotificationMode.ALL_MESSAGES, RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY, RoomNotificationMode.MUTE).forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().selectable(current == m) {
                                modeDialog = null
                                if (dms) dmMode = m else groupMode = m
                                scope.launch { runCatching { account.setDefaultMode(dms, m) }.onFailure { snackbar.showSnackbar(it.userMessage()) } }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(current == m, null)
                            Spacer(Modifier.width(12.dp))
                            Text(modeLabel(m))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { modeDialog = null }) { Text("Close") } },
        )
    }

    if (confirmSignOut) {
        ConfirmDialog(
            "Sign out?",
            if (recovery == RecoveryState.ENABLED) "Your keys are backed up. You'll need your recovery key or another device to read encrypted messages when you sign in again."
            else "Recovery isn't set up. If this is your only device, you'll lose access to your encrypted messages. Set up recovery first in Security.",
            "Sign out",
            destructive = true,
            onDismiss = { confirmSignOut = false },
            onConfirm = {
                signingOut = true
                scope.launch { graph.signOut() }
            },
        )
    }
}
