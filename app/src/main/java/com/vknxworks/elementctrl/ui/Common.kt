package com.vknxworks.elementctrl.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.vknxworks.elementctrl.matrix.mxcAvatar
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Round avatar from an mxc:// URL, or coloured initials when there's no picture. */
@Composable
fun Avatar(name: String, url: String?, id: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val model = remember(url, px) { mxcAvatar(url, (px * 2).coerceAtMost(512)) }
    val fallback: @Composable () -> Unit = {
        Box(
            Modifier.size(size).clip(CircleShape).background(senderColor(id)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials(name),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.42f).sp,
                maxLines = 1,
            )
        }
    }
    if (model == null) {
        Box(modifier) { fallback() }
    } else {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
            loading = { fallback() },
            error = { fallback() },
        )
    }
}

fun initials(name: String): String {
    val clean = name.trim().removePrefix("@").removePrefix("#").removePrefix("!")
    return clean.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#"
}

/** "14:05" today, "Mon" this week, "12 Mar" otherwise. */
fun shortTime(ts: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        DateUtils.isToday(ts) -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ts))
        now.timeInMillis - ts < 6 * DateUtils.DAY_IN_MILLIS -> android.text.format.DateFormat.format("EEE", then).toString()
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> android.text.format.DateFormat.format("d MMM", then).toString()
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ts))
    }
}

fun timeOfDay(ts: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ts))

fun dayLabel(ts: Long): String = when {
    DateUtils.isToday(ts) -> "Today"
    DateUtils.isToday(ts + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> DateFormat.getDateInstance(DateFormat.FULL).format(Date(ts))
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else -> "%.1f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun openUrl(context: Context, url: String) {
    com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn()
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
        .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

/**
 * Copies text to the clipboard. With [sensitive] (recovery keys and other secrets) the clip is flagged so Android
 * hides it from the clipboard preview and keyboards don't keep it in their clipboard history.
 */
fun copyText(context: Context, label: String, text: String, sensitive: Boolean = false) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

/**
 * Decides whether the window gets FLAG_SECURE (blank screenshots, screen recordings and screen sharing) on top of
 * the app-lock setting: screens that show or take secrets (recovery key, password sign-in) force it while shown.
 */
object ScreenPrivacy {
    var forced by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    internal fun acquire() { forced++ }
    internal fun release() { forced = (forced - 1).coerceAtLeast(0) }
}

/** Keeps FLAG_SECURE on for as long as the calling screen is shown, whatever the app-lock setting says. */
@Composable
fun SecureScreen() {
    androidx.compose.runtime.DisposableEffect(Unit) {
        ScreenPrivacy.acquire()
        onDispose { ScreenPrivacy.release() }
    }
}

/** Dialog properties for dialogs that show or take a secret: never captured in screenshots or recordings. */
val SecureDialog = androidx.compose.ui.window.DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn)

fun shareText(context: Context, text: String) {
    com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn(); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

@Composable
fun CenteredLoading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.let { Spacer(Modifier.width(12.dp)); it() }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String = "",
    confirm: String = "Save",
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { if (label.isNotEmpty()) Text(label) },
                singleLine = singleLine,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm(value) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Small unread counter or dot used in the room list. */
@Composable
fun UnreadBadge(count: Long, highlight: Boolean, muted: Boolean) {
    val bg = when {
        muted -> MaterialTheme.colorScheme.outline
        highlight -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val fg = when {
        highlight -> MaterialTheme.colorScheme.onError
        muted -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.onPrimary
    }
    if (count <= 0) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(bg))
    } else {
        Box(
            Modifier.clip(CircleShape).background(bg).padding(horizontal = 7.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (count > 99) "99+" else count.toString(), color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/** True for a plain-http address, where credentials and messages would travel unencrypted. */
fun isCleartext(url: String): Boolean = url.trim().startsWith("http://", ignoreCase = true)

/** Small red "Not encrypted (HTTP)" label shown next to an http homeserver. */
@Composable
fun NotEncryptedBadge(modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            "Not encrypted (HTTP)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** What can stop pushes from turning into notifications, re-checked whenever the screen comes back (e.g. from Settings). */
data class PushHealth(
    /** ElementCtrl is exempt from battery optimisation. */
    val appUnrestricted: Boolean,
    /** ElementCtrl's battery usage is set to "Restricted". */
    val appBackgroundRestricted: Boolean,
    val ntfyInstalled: Boolean,
    /** ntfy is exempt from battery optimisation (only meaningful when installed). */
    val ntfyUnrestricted: Boolean,
)

private fun readPushHealth(context: android.content.Context) = PushHealth(
    appUnrestricted = com.vknxworks.elementctrl.notify.Battery.isUnrestricted(context),
    appBackgroundRestricted = com.vknxworks.elementctrl.notify.Battery.isBackgroundRestricted(context),
    ntfyInstalled = com.vknxworks.elementctrl.notify.UnifiedPush.isNtfyInstalled(context),
    ntfyUnrestricted = com.vknxworks.elementctrl.notify.Battery.isNtfyUnrestricted(context),
)

@Composable
fun rememberPushHealth(): PushHealth {
    val context = androidx.compose.ui.platform.LocalContext.current
    var health by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(readPushHealth(context)) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                health = readPushHealth(context)
                com.vknxworks.elementctrl.notify.Push.recheck(context)
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return health
}

/**
 * Applies "Hide in screenshots" ([secure]: FLAG_SECURE, which also blanks the recent-apps preview) and
 * "Hide in recent apps" ([hideRecents]: a blank card in Recents, screenshots still work) to [activity]'s
 * window, and again whenever they change, so a switch works at once in every open screen. Callers pass the
 * app lock being on as part of [hideRecents]: Android takes the Recents snapshot when you leave, before the
 * app locks, so a preview would otherwise show what's behind the PIN.
 */
@Composable
fun WindowPrivacy(activity: android.app.Activity, secure: Boolean, hideRecents: Boolean) {
    androidx.compose.runtime.LaunchedEffect(secure) {
        if (secure) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
    androidx.compose.runtime.LaunchedEffect(hideRecents) { activity.setRecentsScreenshotEnabled(!hideRecents) }
}
