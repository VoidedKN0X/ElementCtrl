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
import com.vknxworks.elementctrl.ui.SettingRow
import com.vknxworks.elementctrl.ui.copyText
import com.vknxworks.elementctrl.ui.openUrl
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.BackupState
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.VerificationState

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

    fun act(label: String, block: suspend () -> Unit) {
        busy = label
        scope.launch {
            runCatching { block() }.onFailure { snackbar.showSnackbar(it.userMessage()) }
            busy = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Security & recovery") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            val verified = verification == VerificationState.VERIFIED
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (verified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (verified) Icons.Default.CheckCircle else Icons.Default.Warning, null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            when (verification) {
                                VerificationState.VERIFIED -> "This device is verified"
                                VerificationState.UNVERIFIED -> "This device isn't verified"
                                VerificationState.UNKNOWN -> "Checking this device…"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (verified) "It can read your encrypted messages and others can trust it."
                            else "Verify it to read your encrypted history and so others can trust messages from it.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (verification == VerificationState.UNVERIFIED) {
                SectionHeader("Verify this device")
                SettingRow(Icons.Default.Devices, "Use another device", "Compare emojis with a signed-in Element app") { onVerifyDevice() }
                SettingRow(Icons.Default.Key, "Enter recovery key", "The key you saved when you set up recovery") { enterKey = true }
                SettingRow(
                    Icons.Default.LockReset, "Can't confirm? Reset your identity",
                    "Last resort: starts fresh. You'll lose access to old encrypted messages and contacts will need to verify you again.",
                    tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error,
                ) { confirmReset = true }
            }

            SectionHeader("Recovery")
            SettingRow(
                if (recovery == RecoveryState.ENABLED) Icons.Default.CloudDone else Icons.Default.CloudOff,
                "Key storage",
                when (recovery) {
                    RecoveryState.ENABLED -> "On. Your message keys are backed up and protected by your recovery key."
                    RecoveryState.INCOMPLETE -> "Set up, but this device doesn't have the key yet. Enter your recovery key."
                    RecoveryState.DISABLED -> "Off. Set up recovery so you don't lose messages if you lose your devices."
                    RecoveryState.UNKNOWN -> "Checking…"
                } + backupNote(backup),
            )
            when (recovery) {
                RecoveryState.DISABLED -> SettingRow(Icons.Default.Key, "Set up recovery", "Creates a recovery key and backs up your keys") {
                    act("enable") { newKey = account.enableRecovery(null) { } }
                }
                RecoveryState.INCOMPLETE -> SettingRow(Icons.Default.Key, "Enter recovery key") { enterKey = true }
                RecoveryState.ENABLED -> {
                    SettingRow(Icons.Default.Password, "Change recovery key", "Get a new key; the old one stops working") {
                        act("change") { newKey = account.resetRecoveryKey() }
                    }
                    SettingRow(Icons.Default.CloudOff, "Turn off key storage", tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) {
                        confirmDisable = true
                    }
                }
                RecoveryState.UNKNOWN -> Unit
            }
            busy?.let {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Working…")
                }
            }

            SectionHeader("This device")
            SettingRow(null, "Session ID", session.deviceId) { copyText(context, "Session ID", session.deviceId) }
            var keys by remember { mutableStateOf<String?>(null) }
            androidx.compose.runtime.LaunchedEffect(Unit) { keys = runCatching { session.client.encryption().ed25519Key() }.getOrNull() }
            keys?.let { SettingRow(null, "Session key", it.chunked(4).joinToString(" ")) { copyText(context, "Session key", it) } }
            Spacer(Modifier.height(32.dp))
        }
    }

    newKey?.let { key ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Save your recovery key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Store it somewhere safe, like a password manager. You'll need it to read your encrypted messages on a new device.")
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
            confirmButton = { TextButton(onClick = { newKey = null }) { Text("I've saved it") } },
            dismissButton = { TextButton(onClick = { copyText(context, "Recovery key", key) }) { Text("Copy") } },
        )
    }

    if (enterKey) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { enterKey = false },
            title = { Text("Enter recovery key") },
            text = {
                OutlinedTextField(key, { key = it }, label = { Text("Recovery key or passphrase") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(enabled = key.isNotBlank(), onClick = {
                    enterKey = false
                    act("recover") { account.recover(key); snackbar.showSnackbar("Recovered. Your messages will decrypt shortly.") }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { enterKey = false }) { Text("Cancel") } },
        )
    }

    if (confirmDisable) ConfirmDialog(
        "Turn off key storage?",
        "Your keys will be deleted from the server. You may lose access to encrypted messages if you sign out everywhere.",
        "Turn off",
        destructive = true,
        onDismiss = { confirmDisable = false },
        onConfirm = { act("disable") { account.disableRecovery() } },
    )

    if (confirmReset) ConfirmDialog(
        "Reset your identity?",
        "Only do this if you have no other signed-in device and no recovery key. Old encrypted messages may become unreadable and people will need to verify you again.",
        "Continue",
        destructive = true,
        onDismiss = { confirmReset = false },
        onConfirm = {
            act("reset") {
                val r = account.beginIdentityReset()
                if (r == null) snackbar.showSnackbar("Your identity was reset.") else {
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
            title = { Text("Confirm reset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.needsPassword) {
                        Text("Enter your account password to confirm.")
                        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    } else {
                        Text("Approve the reset on your server's page that just opened, then come back and tap Done.")
                        r.approvalUrl?.let { url -> TextButton(onClick = { openUrl(context, url) }) { Text("Open the page again") } }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reset = null
                    act("reset-confirm") { r.confirm(password.ifBlank { null }); snackbar.showSnackbar("Identity reset. Set up recovery again to keep your keys safe.") }
                }) { Text(if (r.needsPassword) "Reset" else "Done") }
            },
            dismissButton = { TextButton(onClick = { reset = null; scope.launch { r.cancel() } }) { Text("Cancel") } },
        )
    }
}

private fun backupNote(state: BackupState): String = when (state) {
    BackupState.CREATING, BackupState.ENABLING -> " Turning on backup…"
    BackupState.RESUMING -> " Resuming backup…"
    BackupState.DOWNLOADING -> " Downloading keys…"
    BackupState.DISABLING -> " Turning off backup…"
    else -> ""
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
            TopAppBar(
                title = { Text(if (otherUserId == null) "Verify this device" else "Verify user") },
                navigationIcon = { IconButton(onClick = { scope.launch { service.cancel() }; finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } },
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
                        if (otherUserId == null) "Open Element (or ElementCtrl) on another signed-in device and accept the request there."
                        else "Waiting for them to accept the request…",
                        textAlign = TextAlign.Center,
                    )
                }
                is VerifyStep.Incoming -> {
                    Text("${s.request.senderName} wants to verify", style = MaterialTheme.typography.titleMedium)
                    Text((s.request.deviceName ?: "") + " " + s.request.deviceId, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { scope.launch { service.acceptIncoming() } }, modifier = Modifier.fillMaxWidth()) { Text("Start verification") }
                    OutlinedButton(onClick = { scope.launch { service.cancel() }; finish() }, modifier = Modifier.fillMaxWidth()) { Text("Ignore") }
                }
                VerifyStep.Accepted, VerifyStep.Starting -> {
                    CircularProgressIndicator()
                    Text("Getting the emojis ready…")
                }
                is VerifyStep.Compare -> {
                    Text("Do these match on the other device?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text("Compare the emojis in the same order.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Button(onClick = { scope.launch { service.confirmMatch() } }, modifier = Modifier.fillMaxWidth()) { Text("They match") }
                    OutlinedButton(
                        onClick = { scope.launch { service.denyMatch() } },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("They don't match") }
                }
                VerifyStep.Confirming -> {
                    CircularProgressIndicator()
                    Text("Waiting for the other side to confirm…")
                }
                VerifyStep.Done -> {
                    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(if (otherUserId == null) "Device verified" else "User verified", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (otherUserId == null) "Your encrypted messages will start decrypting here." else "Messages with them are now marked as trusted.",
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { finish() }) { Text("Done") }
                }
                VerifyStep.Cancelled -> {
                    Text("Verification was cancelled.", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { finish() }) { Text("Close") }
                }
                is VerifyStep.Failed -> {
                    Icon(Icons.Default.Warning, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
                    Text(s.message, textAlign = TextAlign.Center)
                    Button(onClick = { finish() }) { Text("Close") }
                }
            }
        }
    }
}
