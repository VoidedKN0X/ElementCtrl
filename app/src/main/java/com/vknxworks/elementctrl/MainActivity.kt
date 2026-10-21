package com.vknxworks.elementctrl

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.vknxworks.elementctrl.ui.WindowPrivacy
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import com.vknxworks.elementctrl.ui.lock.AppLock
import com.vknxworks.elementctrl.ui.lock.AppLockOverlay
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
import com.vknxworks.elementctrl.ui.isForeignContentUri
import com.vknxworks.elementctrl.ui.onboarding.OnboardingFlow
import org.matrix.rustcomponents.sdk.MatrixId
import org.matrix.rustcomponents.sdk.parseMatrixEntityFrom

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Recents shows a blank card until the settings are read (then "Hide in recent apps" decides).
        setRecentsScreenshotEnabled(false)
        handleIntent(intent)
        setContent {
            ElementCtrlTheme {
                val graph = ElementCtrlApp.graph
                val lock = graph.lock
                // The old PIN lock is still set: unlock with it once, then choose the app PIN.
                val legacyLock = lock.legacyPinSet
                val locked = (legacyLock && !graph.vault.enabled) || (graph.vault.enabled && !AppLock.unlocked)
                // "Hide in screenshots", screens holding secrets (recovery key, password sign-in) and the lock and
                // PIN screens keep the window out of screenshots and recordings; "Hide in recent apps" blanks the card.
                val secure = lock.hideScreenshots || locked || AppLock.request != null || com.vknxworks.elementctrl.ui.ScreenPrivacy.forced > 0
                // "Hide in recent apps" (off by default). With the app lock on, Recents always shows a blank
                // card: a preview there would show your chats without the PIN.
                WindowPrivacy(this@MainActivity, secure, hideRecents = lock.hideRecents || legacyLock || graph.vault.enabled)
                Box(Modifier.fillMaxSize()) {
                    Surface(Modifier.fillMaxSize()) {
                        val state by graph.state.collectAsStateWithLifecycle()
                        // While locked the chats aren't composed at all (so nothing is readable through accessibility
                        // services); their saved state, including the navigation back stack, is kept here so
                        // unlocking returns to the same screen.
                        val savedMain = rememberSaveableStateHolder()
                        if (!locked) {
                            when (val s = state) {
                                AppState.Loading, AppState.Locked -> CenteredLoading()
                                is AppState.SignedOut -> OnboardingFlow(s.message)
                                is AppState.SignedIn -> savedMain.SaveableStateProvider("main:${s.session.userId}") {
                                    // Unverified devices are asked to confirm it's you before the chats appear.
                                    com.vknxworks.elementctrl.ui.onboarding.VerifyGate(s.session) { MainNav(s.session) }
                                }
                            }
                        }
                    }
                    AppLockOverlay(legacyLock)
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
                // Only files from other apps; never file: URIs or our own providers (private-file theft).
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.takeIf { isForeignContentUri(this, it) }
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
