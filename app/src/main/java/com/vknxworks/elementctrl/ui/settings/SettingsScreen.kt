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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.BuildConfig
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.R
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
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import com.vknxworks.elementctrl.data.LockTimeout
import com.vknxworks.elementctrl.data.lock.LockVault
import com.vknxworks.elementctrl.ui.lock.AppLock
import com.vknxworks.elementctrl.ui.lock.PinRequest
import com.vknxworks.elementctrl.ui.lock.findActivity
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.sdkGitSha

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/ElementCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/ElementCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@Composable
fun SettingsScreen(session: MatrixSession, onBack: () -> Unit, onSecurity: () -> Unit, onOpenUser: (String) -> Unit, onDevices: () -> Unit = {}) {
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
    val chatAvatars by prefs.chatAvatars.collectAsStateWithLifecycle()
    val doubleTap by prefs.doubleTapReaction.collectAsStateWithLifecycle()
    val readReceipts by prefs.showReadReceipts.collectAsStateWithLifecycle()
    var doubleTapDialog by remember { mutableStateOf(false) }
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
    val pushStatus by com.vknxworks.elementctrl.notify.Push.status.collectAsStateWithLifecycle()
    var pushDialog by remember { mutableStateOf(false) }
    val linkPreviews by prefs.linkPreviews.collectAsStateWithLifecycle()
    val linkPreviewsE2ee by prefs.linkPreviewsEncrypted.collectAsStateWithLifecycle()
    val lock = graph.lock
    var lockAfterDialog by remember { mutableStateOf(false) }
    val bioAvailable = remember { AppLock.biometricAvailable(context) }
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
                    modifier = Modifier.clickable { com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn(); pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName ?: session.userId, style = MaterialTheme.typography.titleLarge)
                    Text(session.userId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editName = true }) { Icon(Icons.Default.Edit, "Edit name") }
            }
            HorizontalDivider()

            SectionHeader("Appearance")
            SettingRow(Icons.Default.Brightness6, "Theme", when (theme) { ThemeMode.SYSTEM -> "Follow system"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark"; ThemeMode.BLACK -> "Pure black" }) { themeDialog = true }
            SettingRow(Icons.Default.ChatBubbleOutline, "Chat bubbles", bubbleStyle.label + " · " + bubbleStyle.description) { bubbleDialog = true }
            SettingRow(
                Icons.Default.AccountCircle, "Profile pictures",
                "Show pictures next to messages, in direct chats and next to yours too",
                trailing = { Switch(chatAvatars, prefs::setChatAvatars) },
            )
            SettingRow(
                Icons.Default.DoneAll, "Seen by",
                "Small pictures under messages showing who has read up to there",
                trailing = { Switch(readReceipts, prefs::setShowReadReceipts) },
            )
            SettingRow(
                Icons.Default.TouchApp, "Double-tap to react",
                if (doubleTap.isBlank()) "Off" else "Double-tap a message to react with $doubleTap",
            ) { doubleTapDialog = true }
            SettingRow(Icons.Default.Palette, "Use wallpaper colours", "Replaces the default colours with ones from your wallpaper", trailing = { Switch(dynamic, prefs::setDynamicColor) })
            SettingRow(Icons.Default.Visibility, "Show room events", "Joins, leaves, renames and other changes", trailing = { Switch(showState, prefs::setShowStateEvents) })
            SettingRow(Icons.Default.KeyboardReturn, "Enter sends", "Press Enter to send instead of adding a new line", trailing = { Switch(sendOnEnter, prefs::setSendOnEnter) })

            SectionHeader("Notifications")
            SettingRow(
                Icons.Default.Notifications, "Notifications on this device",
                null,
                trailing = {
                    Switch(notifications, { on ->
                        if (on && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn(); askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else prefs.setNotifications(on)
                    })
                },
            )
            val health = com.vknxworks.elementctrl.ui.rememberPushHealth()
            val pushOn = com.vknxworks.elementctrl.notify.Push.isEnabled(context)
            SettingRow(
                Icons.Default.Bolt, "Delivery",
                when (pushStatus) {
                    is com.vknxworks.elementctrl.notify.PushStatus.Active -> "Instant through ntfy, plus a background check every ~15 minutes"
                    com.vknxworks.elementctrl.notify.PushStatus.Registering -> "Setting up push with ntfy…"
                    com.vknxworks.elementctrl.notify.PushStatus.Unavailable -> "ntfy temporarily unavailable: its server can't be reached. The background check keeps running."
                    com.vknxworks.elementctrl.notify.PushStatus.NoNtfy -> "Background check every ~15 minutes until ntfy is installed"
                    is com.vknxworks.elementctrl.notify.PushStatus.Failed -> "Push problem, see below. The background check keeps running."
                    com.vknxworks.elementctrl.notify.PushStatus.Off -> "Background check every ~15 minutes. Tap to turn on instant push through ntfy"
                },
            ) { pushDialog = true }
            if (notifications && !health.ntfyInstalled) {
                PushWarning(
                    Icons.Default.NotificationsOff, "ntfy isn't installed",
                    if (pushOn) {
                        "Instant push comes through ntfy, and it isn't on this phone (any more). Until it's installed, ElementCtrl only checks for new messages every ~15 minutes."
                    } else {
                        "Instant push comes through ntfy, a free app that keeps one connection to your ntfy server. Without it, ElementCtrl checks for new messages every ~15 minutes."
                    },
                    listOf("Install ntfy" to { openUrl(context, com.vknxworks.elementctrl.notify.UnifiedPush.NTFY_DOWNLOAD) }),
                )
            }
            (pushStatus as? com.vknxworks.elementctrl.notify.PushStatus.Failed)?.let { st ->
                PushWarning(
                    Icons.Default.ErrorOutline, "Instant push isn't working",
                    st.message + " Messages still come in with the background check every ~15 minutes.",
                    listOf("Try again" to { com.vknxworks.elementctrl.notify.Push.enable(context); Unit }),
                )
            }
            val appHeldBack = !health.appUnrestricted || health.appBackgroundRestricted
            if (notifications && appHeldBack) {
                PushWarning(
                    Icons.Default.BatteryAlert, "ElementCtrl is battery-restricted",
                    if (health.appBackgroundRestricted) {
                        "Its battery usage is set to Restricted, so Android holds back the job that fetches a pushed message and the background check until you open the app. Set battery usage to Unrestricted."
                    } else {
                        "Battery optimisation cuts ElementCtrl off from the network while the phone sleeps, so a pushed message can't be fetched and notifications come late or not at all. Let it run in the background."
                    },
                    listOf(
                        "Allow" to { com.vknxworks.elementctrl.notify.Battery.requestUnrestricted(context) },
                        "App info" to { com.vknxworks.elementctrl.notify.Battery.openAppInfo(context) },
                    ),
                )
            } else {
                SettingRow(
                    if (appHeldBack) Icons.Default.BatteryAlert else Icons.Default.BatteryFull,
                    "Run in the background",
                    if (appHeldBack) "Restricted by battery optimisation. Tap to allow." else "Allowed. Messages are fetched even while the phone sleeps.",
                ) {
                    if (appHeldBack) com.vknxworks.elementctrl.notify.Battery.requestUnrestricted(context)
                    else com.vknxworks.elementctrl.notify.Battery.openAppInfo(context)
                }
            }
            if (notifications && pushOn && health.ntfyInstalled && !health.ntfyUnrestricted) {
                PushWarning(
                    Icons.Default.BatteryAlert, "ntfy is battery-restricted",
                    "Android can cut ntfy's connection to your ntfy server while the phone sleeps, so pushes arrive late or not at all. In ntfy's App info, open Battery and choose Unrestricted (on ColorOS also turn on \"Allow background activity\"), or open ntfy and accept its battery prompt.",
                    listOf(
                        "ntfy's App info" to { com.vknxworks.elementctrl.notify.Battery.openNtfyAppInfo(context) },
                        "Open ntfy" to { com.vknxworks.elementctrl.notify.Battery.openNtfy(context); Unit },
                    ),
                )
            }
            if (com.vknxworks.elementctrl.notify.Battery.hasVendorLimits) {
                SettingRow(
                    Icons.Default.PhoneAndroid, "OPPO / OnePlus / realme background limits",
                    "Also open App info › Battery and turn on \"Allow background activity\" (and \"Auto launch\" if shown), for ElementCtrl and for ntfy, or ColorOS may still stop them.",
                ) { com.vknxworks.elementctrl.notify.Battery.openAppInfo(context) }
            }
            SettingRow(
                Icons.Default.Visibility, "Show message content in notifications",
                if (graph.vault.enabled && !lock.notificationContentChosen) "Off by default while the app lock is on" else "Sender, chat name and message text",
                trailing = { Switch(lock.showNotificationContent, { lock.showNotificationContent = it }) },
            )
            SettingRow(Icons.Default.Group, "Group chats", modeLabel(groupMode)) { modeDialog = false }
            SettingRow(Icons.Default.ChatBubbleOutline, "Direct chats", modeLabel(dmMode)) { modeDialog = true }
            SettingRow(Icons.Default.AlternateEmail, "Mentions", "Notify when someone mentions you or @room", trailing = {
                Switch(mentions, { on -> mentions = on; scope.launch { runCatching { account.setMentionsEnabled(on) } } })
            })
            SettingRow(Icons.Default.MailOutline, "Invites", "Notify when you're invited to a room", trailing = {
                Switch(invites, { on -> invites = on; scope.launch { runCatching { account.setInvitesEnabled(on) } } })
            })

            SectionHeader(stringResource(R.string.group_privacy_security))
            val lockOn = graph.vault.enabled
            SettingRow(Icons.Outlined.Lock, stringResource(R.string.app_lock), stringResource(R.string.app_lock_subtitle), trailing = {
                Switch(lockOn, { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE })
            }) { AppLock.request = if (lockOn) PinRequest.DISABLE else PinRequest.ENABLE }
            if (lockOn) {
                SettingRow(
                    Icons.Outlined.Password, stringResource(R.string.lock_change_pin),
                    stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                ) { AppLock.request = PinRequest.CHANGE }
                SettingRow(
                    Icons.Outlined.Fingerprint, stringResource(R.string.lock_fingerprint),
                    stringResource(if (bioAvailable || graph.vault.biometricEnabled) R.string.lock_fingerprint_subtitle else R.string.lock_fingerprint_unavailable),
                    trailing = {
                        Switch(graph.vault.biometricEnabled, { on ->
                            if (!on) {
                                graph.vault.disableBiometric()
                            } else {
                                val activity = context.findActivity()
                                val cipher = graph.vault.biometricEnrolCipher()
                                if (activity == null || cipher == null) {
                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.lock_fingerprint_setup_failed)) }
                                } else {
                                    AppLock.biometricPrompt(
                                        activity,
                                        context.getString(R.string.lock_fingerprint_enable_title),
                                        context.getString(R.string.lock_fingerprint_enable_subtitle),
                                        cipher,
                                    ) { unlocked ->
                                        if (!graph.vault.finishBiometricEnrol(unlocked)) {
                                            scope.launch { snackbar.showSnackbar(context.getString(R.string.lock_fingerprint_setup_failed)) }
                                        }
                                    }
                                }
                            }
                        }, enabled = bioAvailable || graph.vault.biometricEnabled)
                    },
                )
                SettingRow(Icons.Outlined.Timer, stringResource(R.string.lock_again), stringResource(lock.lockTimeout.label)) { lockAfterDialog = true }
            }
            SettingRow(
                Icons.Outlined.VisibilityOff, stringResource(R.string.hide_screenshots), stringResource(R.string.hide_screenshots_subtitle),
                trailing = { Switch(lock.hideScreenshots, { lock.hideScreenshots = it }) },
            ) { lock.hideScreenshots = !lock.hideScreenshots }
            SettingRow(
                Icons.Outlined.Layers, stringResource(R.string.hide_recents), stringResource(R.string.hide_recents_subtitle),
                trailing = { Switch(lock.hideRecents, { lock.hideRecents = it }) },
            ) { lock.hideRecents = !lock.hideRecents }
            Text(
                stringResource(
                    when {
                        !lockOn -> R.string.app_lock_footer_off
                        notifications -> R.string.app_lock_footer_background
                        else -> R.string.app_lock_footer
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

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
            SettingRow(Icons.Default.Devices, "Devices", "Phones and computers signed in to your account", onClick = onDevices)
            accountUrl?.let { url ->
                SettingRow(
                    Icons.AutoMirrored.Filled.OpenInNew, "Manage account",
                    "Password, email, signed-in devices",
                ) { openUrl(context, url) }
            }
            SettingRow(
                Icons.Default.Dns, "Homeserver", session.homeserver,
                trailing = { if (com.vknxworks.elementctrl.ui.isCleartext(session.homeserver)) com.vknxworks.elementctrl.ui.NotEncryptedBadge() },
            ) { copyText(context, "Homeserver", session.homeserver) }

            SectionHeader("Chats")
            SettingRow(Icons.Default.Link, "Link previews", "Show a preview card for links, fetched by your homeserver", trailing = { Switch(linkPreviews, prefs::setLinkPreviews) })
            if (linkPreviews) {
                SettingRow(
                    Icons.Default.Lock, "Link previews in encrypted chats",
                    "Your homeserver would see which links are shared in those chats",
                    trailing = { Switch(linkPreviewsE2ee, prefs::setLinkPreviewsEncrypted) },
                )
            }

            if (ignored.isNotEmpty()) {
                SectionHeader("Ignored people")
                ignored.forEach { id ->
                    SettingRow(Icons.Default.Block, id, "Tap to unignore") {
                        scope.launch { runCatching { account.unignore(id) }.onSuccess { ignored = ignored - id } }
                    }
                }
            }

            SectionHeader("Troubleshooting")
            var connectionLog by remember { mutableStateOf<String?>(null) }
            SettingRow(
                Icons.Default.Sync, "Connection log",
                "What the connection did recently, to explain disconnects. No messages, names or servers.",
            ) { connectionLog = com.vknxworks.elementctrl.matrix.ConnectionLog.read().lines().takeLast(400).joinToString("\n") }
            connectionLog?.let { text ->
                AlertDialog(
                    onDismissRequest = { connectionLog = null },
                    title = { Text("Connection log") },
                    text = {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                text.ifBlank { "Nothing logged yet." },
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState(Int.MAX_VALUE)),
                            )
                        }
                    },
                    confirmButton = { TextButton(onClick = { copyText(context, "Connection log", text); connectionLog = null }) { Text("Copy") } },
                    dismissButton = { TextButton(onClick = { connectionLog = null }) { Text("Close") } },
                )
            }

            SettingRow(Icons.Default.Info, "Matrix Rust SDK", runCatching { sdkGitSha().take(8) }.getOrDefault(""))

            HorizontalDivider(Modifier.padding(top = 12.dp))
            SettingRow(Icons.AutoMirrored.Filled.Logout, if (signingOut) "Signing out…" else "Sign out", tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) {
                if (!signingOut) confirmSignOut = true
            }
            HorizontalDivider()

            SectionHeader(stringResource(R.string.group_about))
            val chevron: @Composable () -> Unit = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            SettingRow(Icons.Default.Favorite, stringResource(R.string.support_title), stringResource(R.string.support_subtitle), trailing = chevron) { openUrl(context, SPONSOR_URL) }
            SettingRow(Icons.Default.BugReport, stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), trailing = chevron) { openUrl(context, ISSUES_URL) }
            SettingRow(Icons.Default.Code, stringResource(R.string.source_title), stringResource(R.string.source_subtitle), trailing = chevron) { openUrl(context, REPO_URL) }
            SettingRow(Icons.Default.Gavel, stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), trailing = chevron) { openUrl(context, LICENCE_URL) }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (editName) TextInputDialog("Display name", initial = profile.displayName.orEmpty(), onDismiss = { editName = false }) { name ->
        scope.launch { runCatching { account.setDisplayName(name.trim()) }.onFailure { snackbar.showSnackbar(it.userMessage()) } }
    }

    if (lockAfterDialog) {
        AlertDialog(
            onDismissRequest = { lockAfterDialog = false },
            title = { Text(stringResource(R.string.lock_again)) },
            text = {
                Column {
                    LockTimeout.entries.forEach { t ->
                        Row(Modifier.fillMaxWidth().selectable(lock.lockTimeout == t) { lock.lockTimeout = t; lockAfterDialog = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(lock.lockTimeout == t, null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(t.label))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { lockAfterDialog = false }) { Text(stringResource(R.string.lock_close)) } },
        )
    }

    if (doubleTapDialog) {
        AlertDialog(
            onDismissRequest = { doubleTapDialog = false },
            title = { Text("Double-tap to react") },
            text = {
                Column {
                    listOf("❤️", "👍", "😂", "🔥", "🎉", "👀", "").forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { prefs.setDoubleTapReaction(e); doubleTapDialog = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(doubleTap == e, null)
                            Spacer(Modifier.width(12.dp))
                            Text(if (e.isBlank()) "Off" else e, style = if (e.isBlank()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { doubleTapDialog = false }) { Text("Close") } },
        )
    }

    if (pushDialog) PushSetupDialog(session, com.vknxworks.elementctrl.notify.UnifiedPush.isNtfyInstalled(context), onDismiss = { pushDialog = false })

    if (bubbleDialog) BubbleStylePicker(bubbleStyle, prefs::setBubbleStyle) { bubbleDialog = false }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.BLACK to "Pure black").forEach { (m, label) ->
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
