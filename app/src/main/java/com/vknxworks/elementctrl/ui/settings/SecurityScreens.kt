package com.vknxworks.elementctrl.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.matrix.AccountService
import com.vknxworks.elementctrl.matrix.IdentityReset
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.VerifyStep
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.ui.ConfirmDialog
import com.vknxworks.elementctrl.ui.SectionHeader
import com.vknxworks.elementctrl.ui.Group
import com.vknxworks.elementctrl.ui.GroupRow
import com.vknxworks.elementctrl.ui.Gutter
import com.vknxworks.elementctrl.ui.SecureDialog
import com.vknxworks.elementctrl.ui.SecureScreen
import com.vknxworks.elementctrl.ui.SettingRow
import com.vknxworks.elementctrl.ui.copyText
import com.vknxworks.elementctrl.ui.openUrl
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.BackupState
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.VerificationState
import androidx.compose.ui.res.stringResource
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.ui.ScreenTopBar

/** Device verification, recovery key and key backup. */
@Composable
fun SecurityScreen(session: MatrixSession, onBack: () -> Unit, onVerifyDevice: () -> Unit) {
    val account = remember { AccountService(session) }
    val verification by session.verification.collectAsStateWithLifecycle()
    val recovery by session.recovery.collectAsStateWithLifecycle()
    val backup by session.backup.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var busy by remember { mutableStateOf<String?>(null) }
    var newKey by remember { mutableStateOf<String?>(null) }
    var enterKey by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf<IdentityReset?>(null) }

    // Shows and takes the recovery key: no screenshots or screen recordings while it's open.
    SecureScreen()

    fun act(label: String, block: suspend () -> Unit) {
        busy = label
        scope.launch {
            runCatching { block() }.onFailure { snackbar.showSnackbar(it.userMessage()) }
            busy = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ScreenTopBar(stringResource(R.string.security_recovery), onBack = onBack) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            val verified = verification == VerificationState.VERIFIED
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (verified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (verified) Icons.Default.CheckCircle else Icons.Default.Warning, null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            when (verification) {
                                VerificationState.VERIFIED -> Strings.str(R.string.this_device_is_verified)
                                VerificationState.UNVERIFIED -> Strings.str(R.string.this_device_isn_t_verified)
                                VerificationState.UNKNOWN -> Strings.str(R.string.checking_this_device)
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (verified) stringResource(R.string.it_can_read_your_encrypted_messages)
                            else stringResource(R.string.verify_it_to_read_your_encrypted),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (verification == VerificationState.UNVERIFIED) {
                Group(stringResource(R.string.verify_this_device)) {
                    GroupRow(stringResource(R.string.use_another_device), stringResource(R.string.compare_emojis_with_a_signed_in), Icons.Default.Devices, showChevron = true, onClick = onVerifyDevice)
                    GroupRow(stringResource(R.string.enter_recovery_key), stringResource(R.string.the_key_you_saved_when_you_set_up), Icons.Default.Key, onClick = { enterKey = true })
                    GroupRow(
                        stringResource(R.string.can_t_confirm_reset_your_identity),
                        stringResource(R.string.last_resort_starts_fresh_you_ll_lose),
                        Icons.Default.LockReset,
                        iconTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { confirmReset = true },
                    )
                }
            }

            Group(stringResource(R.string.recovery)) {
                GroupRow(
                    stringResource(R.string.key_storage),
                    when (recovery) {
                        RecoveryState.ENABLED -> Strings.str(R.string.on_your_message_keys_are_backed_up)
                        RecoveryState.INCOMPLETE -> Strings.str(R.string.set_up_but_this_device_doesn_t_have)
                        RecoveryState.DISABLED -> Strings.str(R.string.off_set_up_recovery_so_you_don_t)
                        RecoveryState.UNKNOWN -> Strings.str(R.string.calls_server_checking)
                    }.let { text -> backupNote(backup)?.let { note -> stringResource(R.string.recovery_state_with_note, text, note) } ?: text },
                    if (recovery == RecoveryState.ENABLED) Icons.Default.CloudDone else Icons.Default.CloudOff,
                )
                when (recovery) {
                    RecoveryState.DISABLED -> GroupRow(stringResource(R.string.banner_recovery_title), stringResource(R.string.creates_a_recovery_key_and_backs_up), Icons.Default.Key, onClick = {
                        act("enable") { newKey = account.enableRecovery(null) { } }
                    })
                    RecoveryState.INCOMPLETE -> GroupRow(stringResource(R.string.enter_recovery_key), icon = Icons.Default.Key, onClick = { enterKey = true })
                    RecoveryState.ENABLED -> {
                        GroupRow(stringResource(R.string.change_recovery_key), stringResource(R.string.get_a_new_key_the_old_one_stops), Icons.Default.Password, onClick = {
                            act("change") { newKey = account.resetRecoveryKey() }
                        })
                        GroupRow(
                            stringResource(R.string.turn_off_key_storage),
                            icon = Icons.Default.CloudOff,
                            iconTint = MaterialTheme.colorScheme.error,
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = { confirmDisable = true },
                        )
                    }
                    RecoveryState.UNKNOWN -> Unit
                }
            }
            busy?.let {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.working))
                }
            }

            var keys by remember { mutableStateOf<String?>(null) }
            androidx.compose.runtime.LaunchedEffect(Unit) { keys = runCatching { session.client.encryption().ed25519Key() }.getOrNull() }
            Group(stringResource(R.string.this_device)) {
                GroupRow(stringResource(R.string.session_id_2), session.deviceId, Icons.Default.Fingerprint, onClick = { copyText(context, Strings.str(R.string.session_id_2), session.deviceId) })
                keys?.let { k ->
                    GroupRow(stringResource(R.string.session_key), k.chunked(4).joinToString(" "), Icons.Default.Key, onClick = { copyText(context, Strings.str(R.string.session_key), k) })
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    newKey?.let { key ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.save_your_recovery_key)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.store_it_somewhere_safe_like_a))
                    SelectionContainer {
                        Text(
                            key,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { newKey = null }) { Text(stringResource(R.string.i_ve_saved_it)) } },
            dismissButton = { TextButton(onClick = { copyText(context, Strings.str(R.string.recovery_key), key, sensitive = true) }) { Text(stringResource(R.string.copy)) } },
            properties = SecureDialog,
        )
    }

    if (enterKey) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { enterKey = false },
            title = { Text(stringResource(R.string.enter_recovery_key)) },
            text = {
                OutlinedTextField(
                    key, { key = it },
                    label = { Text(stringResource(R.string.recovery_key_or_passphrase)) },
                    // Password type: keyboards don't suggest, learn or remember what's typed here.
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = key.isNotBlank(), onClick = {
                    enterKey = false
                    val entered = key.trim()
                    busy = "recover"
                    scope.launch {
                        runCatching { account.recover(entered) }
                            .onSuccess { snackbar.showSnackbar(Strings.str(R.string.recovered_your_messages_will_decrypt)) }
                            .onFailure { snackbar.showSnackbar(it.userMessage().replace(entered, "…")) }
                        busy = null
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { enterKey = false }) { Text(stringResource(R.string.lock_cancel)) } },
            properties = SecureDialog,
        )
    }

    if (confirmDisable) ConfirmDialog(
        stringResource(R.string.turn_off_key_storage_2),
        stringResource(R.string.your_keys_will_be_deleted_from_the),
        stringResource(R.string.turn_off),
        destructive = true,
        onDismiss = { confirmDisable = false },
        onConfirm = { act("disable") { account.disableRecovery() } },
    )

    if (confirmReset) ConfirmDialog(
        stringResource(R.string.reset_your_identity),
        stringResource(R.string.only_do_this_if_you_have_no_other),
        stringResource(R.string.continue_text),
        destructive = true,
        onDismiss = { confirmReset = false },
        onConfirm = {
            act("reset") {
                val r = account.beginIdentityReset()
                if (r == null) snackbar.showSnackbar(Strings.str(R.string.your_identity_was_reset)) else {
                    r.approvalUrl?.let { openUrl(context, it) }
                    reset = r
                }
            }
        },
    )

    reset?.let { r ->
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.confirm_reset)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.needsPassword) {
                        Text(stringResource(R.string.enter_your_account_password_to))
                        OutlinedTextField(
                            password, { password = it },
                            label = { Text(stringResource(R.string.password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                            singleLine = true,
                        )
                    } else {
                        Text(stringResource(R.string.approve_the_reset_on_your_server_s))
                        r.approvalUrl?.let { url -> TextButton(onClick = { openUrl(context, url) }) { Text(stringResource(R.string.open_the_page_again)) } }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reset = null
                    act("reset-confirm") { r.confirm(password.ifBlank { null }); snackbar.showSnackbar(Strings.str(R.string.identity_reset_set_up_recovery_again)) }
                }) { Text(if (r.needsPassword) stringResource(R.string.reset) else stringResource(R.string.lock_done)) }
            },
            dismissButton = { TextButton(onClick = { reset = null; scope.launch { r.cancel() } }) { Text(stringResource(R.string.lock_cancel)) } },
            properties = SecureDialog,
        )
    }
}

private fun backupNote(state: BackupState): String? = when (state) {
    BackupState.CREATING, BackupState.ENABLING -> Strings.str(R.string.backup_turning_on)
    BackupState.RESUMING -> Strings.str(R.string.backup_resuming)
    BackupState.DOWNLOADING -> Strings.str(R.string.backup_downloading)
    BackupState.DISABLING -> Strings.str(R.string.backup_turning_off)
    else -> null
}

/** Emoji comparison for verifying this device or another person. */
@Composable
fun VerifyScreen(session: MatrixSession, otherUserIdArg: String?, startOutgoing: Boolean, onDone: () -> Unit) {
    val service = session.verificationService
    val otherUserId = otherUserIdArg ?: service.otherUserId
    val step by service.step.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (startOutgoing) {
            if (otherUserId == null) service.verifyThisDevice() else service.verifyUser(otherUserId)
        }
    }

    fun finish() {
        service.reset()
        onDone()
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = if (otherUserId == null) stringResource(R.string.verify_this_device) else stringResource(R.string.verify_user),
                onBack = { scope.launch { service.cancel() }; finish() },
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = step) {
                VerifyStep.Idle, VerifyStep.Requested -> {
                    CircularProgressIndicator()
                    Text(
                        if (otherUserId == null) stringResource(R.string.open_element_or_elementctrl_on)
                        else stringResource(R.string.waiting_for_them_to_accept_the),
                        textAlign = TextAlign.Center,
                    )
                }
                is VerifyStep.Incoming -> {
                    Text(stringResource(R.string.wants_to_verify, s.request.senderName), style = MaterialTheme.typography.titleMedium)
                    Text((s.request.deviceName ?: "") + " " + s.request.deviceId, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { scope.launch { service.acceptIncoming() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_verification)) }
                    OutlinedButton(onClick = { scope.launch { service.cancel() }; finish() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ignore)) }
                }
                VerifyStep.Accepted, VerifyStep.Starting -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.getting_the_emojis_ready))
                }
                is VerifyStep.Compare -> {
                    Text(stringResource(R.string.do_these_match_on_the_other_device), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.compare_the_emojis_in_the_same_order), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.emojis.isNotEmpty()) {
                        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth().height(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(s.emojis) { e ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(e.symbol, fontSize = 36.sp)
                                    Text(e.description, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    } else {
                        Text(s.decimals.joinToString("  "), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                    }
                    Button(onClick = { scope.launch { service.confirmMatch() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.they_match)) }
                    OutlinedButton(
                        onClick = { scope.launch { service.denyMatch() } },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.they_don_t_match)) }
                }
                VerifyStep.Confirming -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.waiting_for_the_other_side_to))
                }
                VerifyStep.Done -> {
                    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(if (otherUserId == null) stringResource(R.string.device_verified) else stringResource(R.string.user_verified), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (otherUserId == null) stringResource(R.string.your_encrypted_messages_will_start) else stringResource(R.string.messages_with_them_are_now_marked_as),
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { finish() }) { Text(stringResource(R.string.lock_done)) }
                }
                VerifyStep.Cancelled -> {
                    Text(stringResource(R.string.verification_was_cancelled), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { finish() }) { Text(stringResource(R.string.lock_close)) }
                }
                is VerifyStep.Failed -> {
                    Icon(Icons.Default.Warning, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
                    Text(s.message, textAlign = TextAlign.Center)
                    Button(onClick = { finish() }) { Text(stringResource(R.string.lock_close)) }
                }
            }
        }
    }
}
