package com.vknxworks.elementctrl.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.matrix.AccountService
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.VerifyStep
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.ui.SecureScreen
import com.vknxworks.elementctrl.ui.settings.SecurityScreen
import com.vknxworks.elementctrl.ui.settings.VerifyScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.VerificationState

/** "Skip for now" lasts until the app process ends, so the next start asks again. */
private object GateMemory {
    @Volatile var skippedFor: String? = null
}

private enum class GateStep { Choose, Device, Incoming, Key, Reset, Verified }

/**
 * Like Element X: right after sign-in (and on every start while this device is unverified) the app asks you to
 * confirm it's you before showing your chats, with a choice between another signed-in device and your recovery
 * key. Until then this device can't get message keys, so encrypted messages would stay locked.
 */
@Composable
fun VerifyGate(session: MatrixSession, content: @Composable () -> Unit) {
    val verification by session.verification.collectAsStateWithLifecycle()
    val serviceStep by session.verificationService.step.collectAsStateWithLifecycle()
    var step by remember(session) { mutableStateOf<GateStep?>(null) }
    var skipped by remember(session) { mutableStateOf(GateMemory.skippedFor == session.deviceId) }
    // The crypto store usually answers instantly; give it a few seconds before showing chats anyway.
    var waitedForState by remember(session) { mutableStateOf(false) }
    LaunchedEffect(session) { delay(8000); waitedForState = true }

    LaunchedEffect(verification, skipped) {
        if (verification == VerificationState.UNVERIFIED && !skipped && step == null) step = GateStep.Choose
        if (verification == VerificationState.VERIFIED && (step == GateStep.Choose || step == GateStep.Key || step == GateStep.Reset)) {
            step = if (step == GateStep.Key) GateStep.Verified else null
        }
    }
    // Your other device started the verification (e.g. "Verify new session" in Element): answer it here.
    LaunchedEffect(serviceStep) {
        if (serviceStep is VerifyStep.Incoming && step != null && step != GateStep.Device) step = GateStep.Incoming
    }

    when {
        step != null -> GateContent(session, step!!, onStep = { step = it }, onSkip = {
            GateMemory.skippedFor = session.deviceId
            skipped = true
            step = null
        }, onFinished = { step = null })
        verification == VerificationState.UNKNOWN && !waitedForState -> Checking()
        else -> content()
    }
}

@Composable
private fun Checking() {
    Column(
        Modifier.fillMaxSize().systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Setting up encryption…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GateContent(
    session: MatrixSession,
    step: GateStep,
    onStep: (GateStep) -> Unit,
    onSkip: () -> Unit,
    onFinished: () -> Unit,
) {
    val verified = session.verification.collectAsStateWithLifecycle().value == VerificationState.VERIFIED
    fun backToChoice() = if (verified) onFinished() else onStep(GateStep.Choose)
    AnimatedContent(step, label = "verify-gate") { s ->
        when (s) {
            GateStep.Choose -> ChooseMethod(session, onDevice = { onStep(GateStep.Device) }, onKey = { onStep(GateStep.Key) }, onCantConfirm = { onStep(GateStep.Reset) }, onSkip = onSkip)
            GateStep.Device -> VerifyScreen(session, otherUserIdArg = null, startOutgoing = true, onDone = { backToChoice() })
            GateStep.Incoming -> VerifyScreen(session, otherUserIdArg = null, startOutgoing = false, onDone = { backToChoice() })
            GateStep.Key -> RecoveryKeyEntry(session, onBack = { onStep(GateStep.Choose) })
            GateStep.Reset -> {
                BackHandler { onStep(GateStep.Choose) }
                SecurityScreen(session, onBack = { onStep(GateStep.Choose) }, onVerifyDevice = { onStep(GateStep.Device) })
            }
            GateStep.Verified -> VerifiedDone(onFinished)
        }
    }
}

@Composable
private fun ChooseMethod(session: MatrixSession, onDevice: () -> Unit, onKey: () -> Unit, onCantConfirm: () -> Unit, onSkip: () -> Unit) {
    val recovery by session.recovery.collectAsStateWithLifecycle()
    // null while we're still asking the server which devices you have.
    var hasOtherDevices by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(session) {
        hasOtherDevices = runCatching {
            session.client.encryption().waitForE2eeInitializationTasks()
            session.client.encryption().hasDevicesToVerifyAgainst()
        }.getOrDefault(true)
    }
    val keyAvailable = recovery != RecoveryState.DISABLED

    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.VerifiedUser, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Text("Confirm it's you", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Verify this device to read your encrypted messages and so others can trust it. Choose how:",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            MethodCard(
                icon = Icons.Default.Devices,
                title = "Use another device",
                body = when (hasOtherDevices) {
                    false -> "No other signed-in device found. Sign in on Element or ElementCtrl elsewhere first, or use your recovery key."
                    else -> "Approve from Element or ElementCtrl on a phone or computer where you're already signed in, then compare emojis."
                },
                enabled = hasOtherDevices != false,
                loading = hasOtherDevices == null,
                onClick = onDevice,
            )
            MethodCard(
                icon = Icons.Default.Key,
                title = "Use your recovery key",
                body = if (keyAvailable) "The recovery key (security key) you saved when you set up key storage or backup."
                else "Key storage isn't set up on this account, so there's no recovery key yet.",
                enabled = keyAvailable,
                loading = false,
                onClick = onKey,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onCantConfirm) { Text("Can't confirm?") }
            TextButton(onClick = onSkip) { Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                "If you skip, encrypted messages won't decrypt on this device. You'll be asked again next time you open the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MethodCard(icon: ImageVector, title: String, body: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(
                    if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else if (enabled) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun RecoveryKeyEntry(session: MatrixSession, onBack: () -> Unit) {
    val account = remember { AccountService(session) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = !busy) { onBack() }
    // The recovery key unlocks all your encrypted history: no screenshots or screen recordings of it.
    SecureScreen()

    fun submit() {
        if (key.isBlank() || busy) return
        busy = true
        error = null
        scope.launch {
            // Success flips the device to verified, and the gate moves on by itself.
            val entered = key.trim()
            runCatching { account.recover(entered) }
                .onSuccess { key = "" }
                .onFailure {
                    // Never echo the key back, even if an SDK error message happened to contain it.
                    val reason = it.userMessage().replace(entered, "…")
                    error = "That key didn't work. Check it and try again. ($reason)"
                }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recovery key") },
                navigationIcon = { IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Enter the recovery key you saved when you set up key storage. It looks like groups of four letters and numbers. A recovery passphrase works too.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; error = null },
                label = { Text("Recovery key or passphrase") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                // Password type: keyboards don't suggest, learn or remember what's typed here.
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Password,
                ),
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.text?.let { key = it.trim(); error = null } }) {
                        Icon(Icons.Default.ContentPaste, "Paste")
                    }
                },
                minLines = 2,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { submit() }, enabled = key.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Verify")
            }
        }
    }
}

@Composable
private fun VerifiedDone(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Device verified", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Your encrypted messages will start decrypting here.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continue") }
    }
}
