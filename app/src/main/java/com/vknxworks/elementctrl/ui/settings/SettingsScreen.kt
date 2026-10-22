package com.vknxworks.elementctrl.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.data.LockTimeout
import com.vknxworks.elementctrl.data.ThemeMode
import com.vknxworks.elementctrl.data.lock.LockVault
import com.vknxworks.elementctrl.matrix.AccountService
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.notify.Battery
import com.vknxworks.elementctrl.notify.Push
import com.vknxworks.elementctrl.notify.PushStatus
import com.vknxworks.elementctrl.notify.UnifiedPush
import com.vknxworks.elementctrl.ui.Group
import com.vknxworks.elementctrl.ui.GroupChoice
import com.vknxworks.elementctrl.ui.GroupRow
import com.vknxworks.elementctrl.ui.GroupSwitch
import com.vknxworks.elementctrl.ui.ScreenTopBar
import com.vknxworks.elementctrl.ui.copyText
import com.vknxworks.elementctrl.ui.lock.AppLock
import com.vknxworks.elementctrl.ui.lock.PinRequest
import com.vknxworks.elementctrl.ui.lock.findActivity
import com.vknxworks.elementctrl.ui.openUrl
import com.vknxworks.elementctrl.ui.rememberPushHealth
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.sdkGitSha
import com.vknxworks.elementctrl.Strings

/**
 * App settings in the Ctrl series order: Appearance, Notifications, Privacy and security, then ElementCtrl's own
 * groups (Chats, Ignored people, Troubleshooting). Opened from the You page (tap your profile picture).
 * [extraGroups] adds groups after Chats (calls).
 */
@Composable
fun SettingsScreen(
    session: MatrixSession,
    onBack: () -> Unit,
    extraGroups: @Composable () -> Unit = {},
) {
    val graph = ElementCtrlApp.graph
    val prefs = graph.prefs
    val lock = graph.lock
    val account = remember { AccountService(session) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val theme by prefs.theme.collectAsStateWithLifecycle()
    val dynamic by prefs.dynamicColor.collectAsStateWithLifecycle()
    val notifications by prefs.notifications.collectAsStateWithLifecycle()
    val sendOnEnter by prefs.sendOnEnter.collectAsStateWithLifecycle()
    val showState by prefs.showStateEvents.collectAsStateWithLifecycle()
    val bubbleStyle by prefs.bubbleStyle.collectAsStateWithLifecycle()
    val chatAvatars by prefs.chatAvatars.collectAsStateWithLifecycle()
    val doubleTap by prefs.doubleTapReaction.collectAsStateWithLifecycle()
    val readReceipts by prefs.showReadReceipts.collectAsStateWithLifecycle()
    val linkPreviews by prefs.linkPreviews.collectAsStateWithLifecycle()
    val linkPreviewsE2ee by prefs.linkPreviewsEncrypted.collectAsStateWithLifecycle()
    val pushStatus by Push.status.collectAsStateWithLifecycle()
    var bubbleDialog by remember { mutableStateOf(false) }
    var pushDialog by remember { mutableStateOf(false) }
    var groupMode by remember { mutableStateOf<RoomNotificationMode?>(null) }
    var dmMode by remember { mutableStateOf<RoomNotificationMode?>(null) }
    var mentions by remember { mutableStateOf(true) }
    var invites by remember { mutableStateOf(true) }
    var ignored by remember { mutableStateOf<List<String>>(emptyList()) }
    var connectionLog by remember { mutableStateOf<String?>(null) }
    val bioAvailable = remember { AppLock.biometricAvailable(context) }

    LaunchedEffect(Unit) {
        runCatching { groupMode = account.defaultMode(false); dmMode = account.defaultMode(true) }
        runCatching { mentions = account.mentionsEnabled(); invites = account.invitesEnabled() }
        ignored = runCatching { account.ignoredUsers() }.getOrDefault(emptyList())
    }

    val permissionNeeded = stringResource(R.string.notifications_permission_needed)
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.setNotifications(granted)
        if (!granted) {
            // Android won't show the prompt again after a denial: open the app's notification settings instead.
            scope.launch { snackbar.showSnackbar(permissionNeeded) }
            com.vknxworks.elementctrl.notify.NotificationAccess.openSettings(context)
        }
    }

    val modeOptions = listOf(
        RoomNotificationMode.ALL_MESSAGES to stringResource(R.string.mode_all),
        RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY to stringResource(R.string.mode_mentions),
        RoomNotificationMode.MUTE to stringResource(R.string.mode_off),
    )
    fun setMode(dms: Boolean, m: RoomNotificationMode) {
        if (dms) dmMode = m else groupMode = m
        scope.launch { runCatching { account.setDefaultMode(dms, m) }.onFailure { snackbar.showSnackbar(it.userMessage()) } }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ScreenTopBar(stringResource(R.string.settings), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            // ---------- Appearance ----------
            Group(stringResource(R.string.group_appearance)) {
                GroupChoice(
                    stringResource(R.string.theme),
                    listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.BLACK to stringResource(R.string.theme_black),
                    ),
                    theme,
                    icon = Icons.Filled.DarkMode,
                ) { prefs.setTheme(it) }
                GroupSwitch(
                    stringResource(R.string.dynamic_colour),
                    stringResource(R.string.dynamic_colour_subtitle),
                    checked = dynamic,
                    icon = Icons.Filled.Palette,
                ) { prefs.setDynamicColor(it) }
                GroupRow(
                    stringResource(R.string.bubbles),
                    stringResource(R.string.bubble_style_summary, bubbleStyle.label, bubbleStyle.description),
                    icon = Icons.Filled.ChatBubbleOutline,
                    showChevron = true,
                    onClick = { bubbleDialog = true },
                )
            }

            // ---------- Notifications ----------
            val health = rememberPushHealth()
            val pushOn = Push.isEnabled(context)
            val appHeldBack = !health.appUnrestricted || health.appBackgroundRestricted
            Group(stringResource(R.string.group_notifications)) {
                GroupSwitch(
                    stringResource(R.string.notifications_device),
                    stringResource(R.string.notifications_device_subtitle),
                    // Shown as on only when Android actually lets the app show notifications.
                    checked = notifications && health.notificationsAllowed,
                    icon = Icons.Filled.Notifications,
                ) { on ->
                    if (on && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        AppLock.expectReturn()
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (on && !health.notificationsAllowed) {
                        // Permission granted, but notifications (or the Messages channel) are off in Android's settings.
                        prefs.setNotifications(true)
                        com.vknxworks.elementctrl.notify.NotificationAccess.openSettings(context)
                    } else {
                        prefs.setNotifications(on)
                    }
                }
                GroupRow(
                    stringResource(R.string.delivery),
                    stringResource(
                        when (pushStatus) {
                            is PushStatus.Active -> R.string.delivery_active
                            PushStatus.Registering -> R.string.delivery_registering
                            PushStatus.Unavailable -> R.string.delivery_unavailable
                            PushStatus.NoNtfy -> R.string.delivery_no_ntfy
                            is PushStatus.Failed -> R.string.delivery_failed
                            PushStatus.Off -> R.string.delivery_off
                        },
                    ),
                    icon = Icons.Filled.Bolt,
                    showChevron = true,
                    onClick = { pushDialog = true },
                )
                if (notifications && !health.ntfyInstalled) {
                    PushWarning(
                        Icons.Filled.NotificationsOff,
                        stringResource(R.string.warn_no_ntfy_title),
                        stringResource(if (pushOn) R.string.warn_no_ntfy_body_on else R.string.warn_no_ntfy_body_off),
                        listOf(stringResource(R.string.warn_install_ntfy) to { openUrl(context, UnifiedPush.NTFY_DOWNLOAD) }),
                    )
                }
                (pushStatus as? PushStatus.Failed)?.let { st ->
                    PushWarning(
                        Icons.Filled.ErrorOutline,
                        stringResource(R.string.warn_push_failed_title),
                        stringResource(R.string.warn_push_failed_body, st.message),
                        listOf(stringResource(R.string.warn_try_again) to { Push.enable(context); Unit }),
                    )
                }
                if (notifications && appHeldBack) {
                    PushWarning(
                        Icons.Filled.BatteryAlert,
                        stringResource(R.string.warn_battery_title),
                        stringResource(if (health.appBackgroundRestricted) R.string.warn_battery_restricted else R.string.warn_battery_optimised),
                        listOf(
                            stringResource(R.string.warn_allow) to { Battery.requestUnrestricted(context) },
                            stringResource(R.string.warn_app_info) to { Battery.openAppInfo(context) },
                        ),
                    )
                } else {
                    GroupRow(
                        stringResource(R.string.background_run),
                        stringResource(if (appHeldBack) R.string.background_restricted else R.string.background_allowed),
                        icon = if (appHeldBack) Icons.Filled.BatteryAlert else Icons.Filled.BatteryFull,
                        onClick = {
                            if (appHeldBack) Battery.requestUnrestricted(context) else Battery.openAppInfo(context)
                        },
                    )
                }
                if (notifications && pushOn && health.ntfyInstalled && !health.ntfyUnrestricted) {
                    PushWarning(
                        Icons.Filled.BatteryAlert,
                        stringResource(R.string.warn_ntfy_battery_title),
                        stringResource(R.string.warn_ntfy_battery_body),
                        listOf(
                            stringResource(R.string.warn_ntfy_app_info) to { Battery.openNtfyAppInfo(context) },
                            stringResource(R.string.warn_open_ntfy) to { Battery.openNtfy(context); Unit },
                        ),
                    )
                }
                if (Battery.hasVendorLimits) {
                    GroupRow(
                        stringResource(R.string.vendor_limits_title),
                        stringResource(R.string.vendor_limits_body),
                        icon = Icons.Filled.PhoneAndroid,
                        onClick = { Battery.openAppInfo(context) },
                    )
                }
                GroupSwitch(
                    stringResource(R.string.notification_content),
                    stringResource(
                        if (graph.vault.enabled && !lock.notificationContentChosen) R.string.notification_content_locked
                        else R.string.notification_content_subtitle,
                    ),
                    checked = lock.showNotificationContent,
                    icon = Icons.Filled.Visibility,
                ) { lock.showNotificationContent = it }
                GroupChoice(stringResource(R.string.mode_group_chats), modeOptions, groupMode, icon = Icons.Filled.Group) { setMode(false, it) }
                GroupChoice(stringResource(R.string.mode_direct_chats), modeOptions, dmMode, icon = Icons.Filled.ChatBubbleOutline) { setMode(true, it) }
                GroupSwitch(
                    stringResource(R.string.mentions),
                    stringResource(R.string.mentions_subtitle),
                    checked = mentions,
                    icon = Icons.Filled.AlternateEmail,
                ) { on -> mentions = on; scope.launch { runCatching { account.setMentionsEnabled(on) } } }
                GroupSwitch(
                    stringResource(R.string.invites),
                    stringResource(R.string.invites_subtitle),
                    checked = invites,
                    icon = Icons.Filled.MailOutline,
                ) { on -> invites = on; scope.launch { runCatching { account.setInvitesEnabled(on) } } }
            }

            // ---------- Privacy and security (the shared Ctrl series app lock) ----------
            val lockOn = graph.vault.enabled
            val setupFailed = stringResource(R.string.lock_fingerprint_setup_failed)
            Group(
                stringResource(R.string.group_privacy_security),
                footer = stringResource(
                    when {
                        !lockOn -> R.string.app_lock_footer_off
                        notifications -> R.string.app_lock_footer_background
                        else -> R.string.app_lock_footer
                    },
                ),
            ) {
                GroupSwitch(
                    stringResource(R.string.app_lock),
                    stringResource(R.string.app_lock_subtitle),
                    checked = lockOn,
                    icon = Icons.Outlined.Lock,
                ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
                if (lockOn) {
                    GroupRow(
                        stringResource(R.string.lock_change_pin),
                        stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                        icon = Icons.Outlined.Password,
                        showChevron = true,
                        onClick = { AppLock.request = PinRequest.CHANGE },
                    )
                    GroupSwitch(
                        stringResource(R.string.lock_fingerprint),
                        stringResource(if (bioAvailable || graph.vault.biometricEnabled) R.string.lock_fingerprint_subtitle else R.string.lock_fingerprint_unavailable),
                        checked = graph.vault.biometricEnabled,
                        icon = Icons.Outlined.Fingerprint,
                        enabled = bioAvailable || graph.vault.biometricEnabled,
                    ) { on ->
                        if (!on) {
                            graph.vault.disableBiometric()
                        } else {
                            val activity = context.findActivity()
                            val cipher = graph.vault.biometricEnrolCipher()
                            if (activity == null || cipher == null) {
                                scope.launch { snackbar.showSnackbar(setupFailed) }
                            } else {
                                AppLock.biometricPrompt(
                                    activity,
                                    context.getString(R.string.lock_fingerprint_enable_title),
                                    context.getString(R.string.lock_fingerprint_enable_subtitle),
                                    cipher,
                                ) { unlocked ->
                                    if (!graph.vault.finishBiometricEnrol(unlocked)) scope.launch { snackbar.showSnackbar(setupFailed) }
                                }
                            }
                        }
                    }
                    GroupChoice(
                        stringResource(R.string.lock_again),
                        LockTimeout.entries.map { it to stringResource(it.label) },
                        lock.lockTimeout,
                        icon = Icons.Outlined.Timer,
                    ) { lock.lockTimeout = it }
                }
                GroupSwitch(
                    stringResource(R.string.hide_screenshots),
                    stringResource(R.string.hide_screenshots_subtitle),
                    checked = lock.hideScreenshots,
                    icon = Icons.Outlined.VisibilityOff,
                ) { lock.hideScreenshots = it }
                GroupSwitch(
                    stringResource(R.string.hide_recents),
                    stringResource(R.string.hide_recents_subtitle),
                    checked = lock.hideRecents,
                    icon = Icons.Outlined.Layers,
                ) { lock.hideRecents = it }
            }

            // ---------- Chats ----------
            Group(stringResource(R.string.group_chats)) {
                GroupSwitch(
                    stringResource(R.string.chat_avatars),
                    stringResource(R.string.chat_avatars_subtitle),
                    checked = chatAvatars,
                    icon = Icons.Filled.AccountCircle,
                ) { prefs.setChatAvatars(it) }
                GroupSwitch(
                    stringResource(R.string.read_receipts),
                    stringResource(R.string.read_receipts_subtitle),
                    checked = readReceipts,
                    icon = Icons.Filled.DoneAll,
                ) { prefs.setShowReadReceipts(it) }
                GroupChoice(
                    stringResource(R.string.double_tap),
                    listOf("❤️", "👍", "😂", "🔥", "🎉", "👀").map { it to stringResource(R.string.double_tap_with, it) } +
                        ("" to stringResource(R.string.double_tap_off)),
                    doubleTap,
                    icon = Icons.Filled.TouchApp,
                ) { prefs.setDoubleTapReaction(it) }
                GroupSwitch(
                    stringResource(R.string.room_events),
                    stringResource(R.string.room_events_subtitle),
                    checked = showState,
                    icon = Icons.Filled.Visibility,
                ) { prefs.setShowStateEvents(it) }
                GroupSwitch(
                    stringResource(R.string.enter_sends),
                    stringResource(R.string.enter_sends_subtitle),
                    checked = sendOnEnter,
                    icon = Icons.Filled.KeyboardReturn,
                ) { prefs.setSendOnEnter(it) }
                GroupSwitch(
                    stringResource(R.string.link_previews),
                    stringResource(R.string.link_previews_subtitle),
                    checked = linkPreviews,
                    icon = Icons.Filled.Link,
                ) { prefs.setLinkPreviews(it) }
                if (linkPreviews) {
                    GroupSwitch(
                        stringResource(R.string.link_previews_e2ee),
                        stringResource(R.string.link_previews_e2ee_subtitle),
                        checked = linkPreviewsE2ee,
                        icon = Icons.Filled.Lock,
                    ) { prefs.setLinkPreviewsEncrypted(it) }
                }
            }

            extraGroups()

            if (ignored.isNotEmpty()) {
                Group(stringResource(R.string.group_ignored)) {
                    ignored.forEach { id ->
                        GroupRow(id, stringResource(R.string.ignored_unignore), icon = Icons.Filled.Block, onClick = {
                            scope.launch { runCatching { account.unignore(id) }.onSuccess { ignored = ignored - id } }
                        })
                    }
                }
            }

            // ---------- Troubleshooting ----------
            Group(stringResource(R.string.group_troubleshooting)) {
                GroupRow(
                    stringResource(R.string.connection_log),
                    stringResource(R.string.connection_log_subtitle),
                    icon = Icons.Filled.Sync,
                    showChevron = true,
                    onClick = { connectionLog = com.vknxworks.elementctrl.matrix.ConnectionLog.read().lines().takeLast(400).joinToString("\n") },
                )
                GroupRow(
                    stringResource(R.string.sdk_version),
                    runCatching { sdkGitSha().take(8) }.getOrDefault(""),
                    icon = Icons.Filled.Info,
                )
            }
        }
    }

    connectionLog?.let { text ->
        AlertDialog(
            onDismissRequest = { connectionLog = null },
            title = { Text(stringResource(R.string.connection_log)) },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text.ifBlank { stringResource(R.string.connection_log_empty) },
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState(Int.MAX_VALUE)),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { copyText(context, Strings.str(R.string.connection_log), text); connectionLog = null }) { Text(stringResource(R.string.copy)) } },
            dismissButton = { TextButton(onClick = { connectionLog = null }) { Text(stringResource(R.string.close)) } },
        )
    }

    if (pushDialog) PushSetupDialog(session, UnifiedPush.isNtfyInstalled(context), onDismiss = { pushDialog = false })

    if (bubbleDialog) BubbleStylePicker(bubbleStyle, prefs::setBubbleStyle) { bubbleDialog = false }
}
