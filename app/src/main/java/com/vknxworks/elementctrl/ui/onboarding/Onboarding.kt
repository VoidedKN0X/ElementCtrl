package com.vknxworks.elementctrl.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.data.ServerOption
import com.vknxworks.elementctrl.data.Servers
import com.vknxworks.elementctrl.matrix.ServerDetails
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.ui.ConfirmDialog
import com.vknxworks.elementctrl.ui.DeepLinks
import com.vknxworks.elementctrl.ui.NotEncryptedBadge
import com.vknxworks.elementctrl.ui.SecureScreen
import com.vknxworks.elementctrl.ui.isCleartext
import com.vknxworks.elementctrl.ui.openUrl
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.ui.ScreenTopBar

/** First start: pick a homeserver, then sign in to it. */
@Composable
fun OnboardingFlow(signedOutMessage: String?) {
    val graph = ElementCtrlApp.graph
    var server by remember { mutableStateOf<ServerDetails?>(null) }
    AnimatedContent(targetState = server, label = "onboarding") { details ->
        if (details == null) {
            ServerPickerScreen(message = signedOutMessage, onServerReady = { server = it })
        } else {
            BackHandler { graph.auth.reset(); server = null }
            LoginScreen(details, onBack = { graph.auth.reset(); server = null })
        }
    }
}

@Composable
fun ServerPickerScreen(message: String?, onServerReady: (ServerDetails) -> Unit) {
    val graph = ElementCtrlApp.graph
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var custom by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(message) }
    val recent = remember { graph.prefs.recentCustomServers }

    // An http homeserver would get your password and access token in plain text: ask before using one.
    var insecure by remember { mutableStateOf<String?>(null) }
    var insecureDetails by remember { mutableStateOf<ServerDetails?>(null) }

    fun discover(target: String, httpConfirmed: Boolean) {
        busy = target
        error = null
        scope.launch {
            runCatching { graph.auth.discover(target) }
                .onSuccess { details ->
                    if (!httpConfirmed && isCleartext(details.url)) {
                        // .well-known pointed a bare server name at plain http: never downgrade silently.
                        insecureDetails = details
                    } else {
                        if (Servers.standard.none { it.address.equals(target, true) }) graph.prefs.rememberCustomServer(target)
                        onServerReady(details)
                    }
                }
                .onFailure { error = it.userMessage() }
            busy = null
        }
    }

    fun connect(address: String) {
        val target = Servers.normalise(address)
        if (target.isBlank() || busy != null) return
        focus.clearFocus()
        if (isCleartext(target)) { insecure = target; return }
        discover(target, httpConfirmed = false)
    }

    insecure?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.not_encrypted_2),
            text = stringResource(R.string.http_warning_body, target),
            confirm = stringResource(R.string.connect_anyway),
            destructive = true,
            onDismiss = { insecure = null },
            onConfirm = { discover(target, httpConfirmed = true) },
        )
    }
    insecureDetails?.let { details ->
        ConfirmDialog(
            title = stringResource(R.string.not_encrypted_2),
            text = stringResource(R.string.http_warning_wellknown, details.address, details.url),
            confirm = stringResource(R.string.connect_anyway),
            destructive = true,
            // ConfirmDialog calls onDismiss before onConfirm, so only clear the dialog here.
            onDismiss = { insecureDetails = null },
            onConfirm = {
                if (Servers.standard.none { it.address.equals(details.address, true) }) graph.prefs.rememberCustomServer(details.address)
                onServerReady(details)
            },
        )
    }

    Scaffold { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("[ ]", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.welcome_to_elementctrl), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.secure_decentralised_chat_on_matrix),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            error?.let { e ->
                item {
                    Card(shape = com.vknxworks.elementctrl.ui.CtrlShapes.Card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(e, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Text(stringResource(R.string.popular_servers), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
            items(Servers.standard, key = { it.address }) { option ->
                ServerCard(option, Icons.Outlined.Public, busy == option.address, enabled = busy == null) { connect(option.address) }
            }
            if (recent.isNotEmpty()) {
                item { Text(stringResource(R.string.your_servers), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                items(recent, key = { "recent:$it" }) { address ->
                    ServerCard(ServerOption(address, address, Strings.str(R.string.signed_in_here_before)), Icons.Outlined.History, busy == address, enabled = busy == null) { connect(address) }
                }
            }
            item {
                Text(stringResource(R.string.self_hosted_or_other_server), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Card(shape = com.vknxworks.elementctrl.ui.CtrlShapes.Card, border = androidx.compose.foundation.BorderStroke(1.dp, com.vknxworks.elementctrl.ui.CtrlColors.Border), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.your_own_synapse_conduit_or_dendrite), style = MaterialTheme.typography.bodyMedium)
                        }
                        com.vknxworks.elementctrl.ui.components.CtrlTextField(
                            value = custom,
                            onValueChange = { custom = it },
                            label = { Text(stringResource(R.string.server_address)) },
                            placeholder = { Text(stringResource(R.string.example_com_or_https_matrix_example)) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Language, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onGo = { connect(custom) }),
                            enabled = busy == null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.your_server_needs_simplified_sliding),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        com.vknxworks.elementctrl.ui.components.CtrlButton(
                            onClick = { connect(custom) },
                            enabled = custom.isNotBlank() && busy == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy != null && busy == Servers.normalise(custom)) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = com.vknxworks.elementctrl.ui.CtrlColors.OnAccent)
                            } else {
                                Text(stringResource(R.string.continue_text))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(option: ServerOption, icon: androidx.compose.ui.graphics.vector.ImageVector, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(shape = com.vknxworks.elementctrl.ui.CtrlShapes.Card, border = androidx.compose.foundation.BorderStroke(1.dp, com.vknxworks.elementctrl.ui.CtrlColors.Border), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clip(CardDefaults.shape).clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(option.name, style = MaterialTheme.typography.titleMedium)
                Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LoginScreen(server: ServerDetails, onBack: () -> Unit) {
    val graph = ElementCtrlApp.graph
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var username by rememberSaveable { mutableStateOf("") }
    // Not rememberSaveable: the password must never end up in the saved-instance-state Bundle.
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    // No screenshots or recordings of the password (it can be shown in plain text).
    SecureScreen()
    var busy by remember { mutableStateOf(false) }
    var waitingForBrowser by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // The browser sends the person back here after OAuth or SSO sign-in.
    LaunchedEffect(Unit) {
        DeepLinks.authRedirect.collect { url ->
            DeepLinks.authRedirect.resetReplayCache()
            busy = true
            error = null
            runCatching { graph.auth.completeFromRedirect(url) }.onFailure { error = it.userMessage() }
            busy = false
            waitingForBrowser = false
        }
    }

    fun browser(block: suspend () -> String) {
        error = null
        busy = true
        scope.launch {
            runCatching { block() }
                .onSuccess { waitingForBrowser = true; openUrl(context, it) }
                .onFailure { error = it.userMessage() }
            busy = false
        }
    }

    fun passwordLogin() {
        if (username.isBlank() || password.isEmpty() || busy) return
        focus.clearFocus()
        busy = true
        error = null
        scope.launch {
            runCatching { graph.auth.loginWithPassword(username, password) }.onFailure { error = it.userMessage() }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.sign_in),
                onBack = onBack,
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(shape = com.vknxworks.elementctrl.ui.CtrlShapes.Card, border = androidx.compose.foundation.BorderStroke(1.dp, com.vknxworks.elementctrl.ui.CtrlColors.Border), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(server.address, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(server.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        if (isCleartext(server.url)) NotEncryptedBadge(Modifier.padding(top = 4.dp))
                    }
                    TextButton(onClick = onBack) { Text(stringResource(R.string.change)) }
                }
            }

            error?.let {
                Card(shape = com.vknxworks.elementctrl.ui.CtrlShapes.Card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            if (waitingForBrowser) {
                Text(
                    stringResource(R.string.finish_signing_in_in_the_browser_you),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (server.supportsOAuth) {
                Text(
                    stringResource(R.string.this_server_signs_you_in_through_its),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.vknxworks.elementctrl.ui.components.CtrlButton(onClick = { browser { graph.auth.oauthUrl(register = false) } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.continue_to_sign_in))
                }
                if (server.canRegister) {
                    com.vknxworks.elementctrl.ui.components.CtrlSecondaryButton(onClick = { browser { graph.auth.oauthUrl(register = true) } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(R.string.create_an_account))
                    }
                }
            }

            if (server.supportsPassword && !server.supportsOAuth) {
                com.vknxworks.elementctrl.ui.components.CtrlTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username_or_matrix_id)) },
                    placeholder = { Text(stringResource(R.string.alice_or_alice, server.address)) },
                    leadingIcon = { Icon(Icons.Outlined.Person, null) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                com.vknxworks.elementctrl.ui.components.CtrlTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    leadingIcon = { Icon(Icons.Outlined.Key, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showPassword) stringResource(R.string.hide) else stringResource(R.string.show))
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { passwordLogin() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                com.vknxworks.elementctrl.ui.components.CtrlButton(onClick = { passwordLogin() }, enabled = !busy && username.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = com.vknxworks.elementctrl.ui.CtrlColors.OnAccent)
                    else Text(stringResource(R.string.sign_in))
                }
                Text(
                    stringResource(R.string.to_create_an_account_on_this_server),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (server.supportsSso && !server.supportsOAuth) {
                if (server.supportsPassword) HorizontalDivider()
                com.vknxworks.elementctrl.ui.components.CtrlSecondaryButton(onClick = { browser { graph.auth.ssoUrl() } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.continue_with_single_sign_on))
                }
            }

            if (!server.supportsOAuth && !server.supportsPassword && !server.supportsSso) {
                Text(stringResource(R.string.this_server_doesn_t_offer_a_sign_in), color = MaterialTheme.colorScheme.error)
            }

            if (busy && (server.supportsOAuth || waitingForBrowser)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            Spacer(Modifier.height(24.dp).systemBarsPadding())
        }
    }
}
