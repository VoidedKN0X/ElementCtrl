package com.vknxworks.elementctrl.call

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.ui.Group
import com.vknxworks.elementctrl.ui.GroupRow
import com.vknxworks.elementctrl.ui.lock.AppLock
import com.vknxworks.elementctrl.ui.openUrl

/** Settings › Calls: whether your server is set up for calls, and full-screen ringing. */
@Composable
fun CallSettingsGroup(session: MatrixSession) {
    val context = LocalContext.current
    var support by remember { mutableStateOf<CallSupport.Result?>(null) }
    LaunchedEffect(session) { support = CallSupport.check(session) }
    val fullScreen = remember {
        android.os.Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    Group(stringResource(R.string.group_calls), footer = stringResource(R.string.calls_footer)) {
        GroupRow(
            stringResource(R.string.calls_server),
            when (val s = support) {
                null -> stringResource(R.string.calls_server_checking)
                is CallSupport.Result.Ready -> stringResource(
                    R.string.calls_server_ready,
                    s.serviceUrls.joinToString { runCatching { Uri.parse(it).host }.getOrNull() ?: it },
                )
                CallSupport.Result.NotSetUp -> stringResource(R.string.calls_server_missing)
                is CallSupport.Result.Failed -> stringResource(R.string.call_check_failed, s.message)
            },
            icon = Icons.Outlined.Dns,
            iconTint = if (support == CallSupport.Result.NotSetUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            showChevron = support !is CallSupport.Result.Ready,
            onClick = if (support is CallSupport.Result.Ready) null else ({ openUrl(context, CALLS_HELP_URL) }),
        )
        GroupRow(
            stringResource(R.string.calls_full_screen),
            stringResource(if (fullScreen) R.string.calls_full_screen_on else R.string.calls_full_screen_off),
            icon = Icons.Outlined.PhoneInTalk,
            iconTint = if (fullScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            showChevron = !fullScreen,
            onClick = if (fullScreen) null else ({
                AppLock.expectReturn()
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                Unit
            }),
        )
    }
}
