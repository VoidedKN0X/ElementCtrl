package com.vknxworks.elementctrl.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.BuildConfig
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.matrix.AccountService
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.ui.Avatar
import com.vknxworks.elementctrl.ui.ProfileAvatar
import com.vknxworks.elementctrl.ui.ConfirmDialog
import com.vknxworks.elementctrl.ui.Group
import com.vknxworks.elementctrl.ui.GroupRow
import com.vknxworks.elementctrl.ui.Gutter
import com.vknxworks.elementctrl.ui.NotEncryptedBadge
import com.vknxworks.elementctrl.ui.ScreenTopBar
import com.vknxworks.elementctrl.ui.TextInputDialog
import com.vknxworks.elementctrl.ui.copyText
import com.vknxworks.elementctrl.ui.isCleartext
import com.vknxworks.elementctrl.ui.openUrl
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.RecoveryState
import com.vknxworks.elementctrl.Strings

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/ElementCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/ElementCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/**
 * The You page, opened from your profile picture at the top right of the main screens (the only way into
 * settings, as in the other Ctrl apps): who you're signed in as, your account, Settings, Sign out and About.
 */
@Composable
fun YouScreen(
    session: MatrixSession,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onSecurity: () -> Unit,
    onDevices: () -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val graph = ElementCtrlApp.graph
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val account = remember { AccountService(session) }
    val profile by session.profile.collectAsStateWithLifecycle()
    val recovery by session.recovery.collectAsStateWithLifecycle()
    var accountUrl by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    val copied = stringResource(R.string.account_homeserver_copied)

    LaunchedEffect(Unit) { accountUrl = runCatching { account.accountManagementUrl() }.getOrNull() }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { scope.launch { runCatching { account.setAvatar(context, it) }.onFailure { e -> snackbar.showSnackbar(e.userMessage()) } } }
    }
    fun changePicture() {
        com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn()
        pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val name = profile.displayName?.takeIf { it.isNotBlank() } ?: session.userId
    val server = remember(session.homeserver) {
        runCatching { android.net.Uri.parse(session.homeserver).host }.getOrNull() ?: session.homeserver
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ScreenTopBar(stringResource(R.string.you_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val pictureLabel = stringResource(R.string.you_change_picture)
                Box(Modifier.clickable(onClickLabel = pictureLabel) { changePicture() }) {
                    ProfileAvatar(name, profile.avatarUrl, 96.dp, MaterialTheme.typography.headlineLarge)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomEnd).size(30.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PhotoCamera, pictureLabel, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    listOf(session.userId, server).distinct().joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Group(stringResource(R.string.group_account)) {
                GroupRow(
                    stringResource(R.string.account_display_name),
                    profile.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.account_display_name_empty),
                    icon = Icons.Filled.Edit,
                    showChevron = true,
                    onClick = { editName = true },
                )
                GroupRow(
                    stringResource(R.string.account_profile),
                    stringResource(R.string.account_profile_subtitle),
                    icon = Icons.Filled.Person,
                    showChevron = true,
                    onClick = { onOpenUser(session.userId) },
                )
                GroupRow(
                    stringResource(R.string.account_security),
                    stringResource(
                        when (recovery) {
                            RecoveryState.ENABLED -> R.string.account_security_on
                            RecoveryState.DISABLED -> R.string.account_security_off
                            RecoveryState.INCOMPLETE -> R.string.account_security_incomplete
                            RecoveryState.UNKNOWN -> R.string.account_security_unknown
                        },
                    ),
                    icon = Icons.Filled.Security,
                    showChevron = true,
                    onClick = onSecurity,
                )
                GroupRow(
                    stringResource(R.string.account_devices),
                    stringResource(R.string.account_devices_subtitle),
                    icon = Icons.Filled.Devices,
                    showChevron = true,
                    onClick = onDevices,
                )
                accountUrl?.let { url ->
                    GroupRow(
                        stringResource(R.string.account_manage),
                        stringResource(R.string.account_manage_subtitle),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        showChevron = true,
                        onClick = { openUrl(context, url) },
                    )
                }
                GroupRow(
                    stringResource(R.string.account_homeserver),
                    session.homeserver,
                    icon = Icons.Filled.Dns,
                    onClick = {
                        copyText(context, Strings.str(R.string.account_homeserver), session.homeserver)
                        scope.launch { snackbar.showSnackbar(copied) }
                    },
                    trailing = { if (isCleartext(session.homeserver)) NotEncryptedBadge() },
                )
            }

            Group {
                GroupRow(
                    stringResource(R.string.settings),
                    stringResource(R.string.settings_subtitle),
                    icon = Icons.Filled.Settings,
                    showChevron = true,
                    onClick = onSettings,
                )
                GroupRow(
                    stringResource(if (signingOut) R.string.signing_out else R.string.sign_out),
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { if (!signingOut) confirmSignOut = true },
                )
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(stringResource(R.string.support_title), stringResource(R.string.support_subtitle), icon = Icons.Filled.Favorite, showChevron = true, onClick = { openUrl(context, SPONSOR_URL) })
                GroupRow(stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), icon = Icons.Filled.BugReport, showChevron = true, onClick = { openUrl(context, ISSUES_URL) })
                GroupRow(stringResource(R.string.source_title), stringResource(R.string.source_subtitle), icon = Icons.Filled.Code, showChevron = true, onClick = { openUrl(context, REPO_URL) })
                GroupRow(stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), icon = Icons.Filled.Gavel, showChevron = true, onClick = { openUrl(context, LICENCE_URL) })
            }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (editName) {
        TextInputDialog(
            stringResource(R.string.account_display_name),
            initial = profile.displayName.orEmpty(),
            confirm = stringResource(R.string.save),
            onDismiss = { editName = false },
        ) { newName ->
            scope.launch { runCatching { account.setDisplayName(newName.trim()) }.onFailure { snackbar.showSnackbar(it.userMessage()) } }
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            stringResource(R.string.sign_out_title),
            stringResource(if (recovery == RecoveryState.ENABLED) R.string.sign_out_body_backed_up else R.string.sign_out_body_no_backup),
            stringResource(R.string.sign_out),
            destructive = true,
            onDismiss = { confirmSignOut = false },
            onConfirm = {
                signingOut = true
                scope.launch { graph.signOut() }
            },
        )
    }
}
