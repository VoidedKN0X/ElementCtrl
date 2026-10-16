package com.vknxworks.elementctrl

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.elementctrl.ui.lock.AppLockViewModel
import com.vknxworks.elementctrl.ui.lock.LockScreen
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.matrix.AppState
import com.vknxworks.elementctrl.notify.Notifier
import com.vknxworks.elementctrl.ui.CenteredLoading
import com.vknxworks.elementctrl.ui.DeepLinks
import com.vknxworks.elementctrl.ui.ElementCtrlTheme
import com.vknxworks.elementctrl.ui.MainNav
import com.vknxworks.elementctrl.ui.SharedContent
import com.vknxworks.elementctrl.ui.onboarding.OnboardingFlow
import org.matrix.rustcomponents.sdk.MatrixId
import org.matrix.rustcomponents.sdk.parseMatrixEntityFrom

// FragmentActivity (a ComponentActivity) is needed for the fingerprint prompt of the app lock.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            ElementCtrlTheme {
                val lock = ElementCtrlApp.graph.lock
                val secure = lock.pinSet && lock.hideContent
                LaunchedEffect(secure) {
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                Surface(Modifier.fillMaxSize()) {
                    val state by ElementCtrlApp.graph.state.collectAsStateWithLifecycle()
                    val lockVm: AppLockViewModel = viewModel()
                    val owner = LocalLifecycleOwner.current
                    DisposableEffect(owner) {
                        val obs = LifecycleEventObserver { _, e ->
                            when (e) {
                                Lifecycle.Event.ON_STOP -> if (!isChangingConfigurations) lockVm.onBackground()
                                Lifecycle.Event.ON_START -> lockVm.onForeground()
                                else -> Unit
                            }
                        }
                        owner.lifecycle.addObserver(obs)
                        onDispose { owner.lifecycle.removeObserver(obs) }
                    }
                    val scope = rememberCoroutineScope()
                    when (val s = state) {
                        AppState.Loading -> CenteredLoading()
                        is AppState.SignedOut -> OnboardingFlow(s.message)
                        // The lock covers the app instead of replacing it, so you come back to where you were.
                        is AppState.SignedIn -> androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                            MainNav(s.session)
                            if (lockVm.locked) {
                                LockScreen(lockVm, onForgot = {
                                    scope.launch {
                                        lock.clearPin()
                                        lockVm.unlock()
                                        ElementCtrlApp.graph.signOut()
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notifier.EXTRA_ROOM_ID)?.let {
            DeepLinks.openRoom.value = it
            intent.removeExtra(Notifier.EXTRA_ROOM_ID)
            return
        }
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let(::handleUri)
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (text != null || stream != null) DeepLinks.share.value = SharedContent(text, stream)
            }
        }
    }

    private fun handleUri(uri: Uri) {
        val scheme = getString(R.string.login_redirect_scheme)
        if (uri.scheme == scheme) {
            DeepLinks.authRedirect.tryEmit(uri.toString())
            return
        }
        val entity = runCatching { parseMatrixEntityFrom(uri.toString()) }.getOrNull() ?: return
        when (val id = entity.id) {
            is MatrixId.User -> DeepLinks.openUser.value = id.id
            is MatrixId.Room -> DeepLinks.joinTarget.value = id.id to entity.via
            is MatrixId.RoomAlias -> DeepLinks.joinTarget.value = id.alias to entity.via
            is MatrixId.EventOnRoomId -> DeepLinks.joinTarget.value = id.roomId to entity.via
            is MatrixId.EventOnRoomAlias -> DeepLinks.joinTarget.value = id.alias to entity.via
        }
    }
}
