package com.vknxworks.elementctrl.matrix

import android.content.Context
import android.util.Log
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.data.Prefs
import com.vknxworks.elementctrl.data.SessionStore
import com.vknxworks.elementctrl.data.StoredSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface AppState {
    data object Loading : AppState
    data class SignedOut(val message: String? = null) : AppState
    data class SignedIn(val session: MatrixSession) : AppState
}

/** App-wide singletons: settings, the stored session, the sign-in flow and the live Matrix session. */
class AppGraph(val context: Context) {
    val prefs = Prefs(context)
    val store = SessionStore(context)
    val factory = ClientFactory(context, store)
    val auth = AuthService(this)
    val redirectScheme: String = context.getString(R.string.login_redirect_scheme)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    val session: MatrixSession? get() = (_state.value as? AppState.SignedIn)?.session

    private val mutex = Mutex()

    /** Opens the saved account, if any. Runs once at app start. */
    fun boot() {
        scope.launch {
            val stored = withContext(Dispatchers.IO) { store.load() }
            if (stored == null) {
                _state.value = AppState.SignedOut()
            } else if (restore(stored) == null) {
                _state.value = AppState.SignedOut("Couldn't open your saved session. Please sign in again.")
            }
        }
    }

    /** Builds a client for a stored session and starts syncing. */
    suspend fun restore(stored: StoredSession): MatrixSession? = mutex.withLock {
        session?.let { if (it.userId == stored.userId) return it }
        withContext(Dispatchers.IO) {
            runCatching {
                val paths = factory.pathsFor(stored.directory, stored.databaseKey)
                val client = factory.builder(paths, StoringSessionDelegate(store))
                    .homeserverUrl(stored.homeserverUrl)
                    .build()
                client.restoreSession(factory.toSession(stored))
                val sync = client.syncService().withOfflineMode().withSharePos(true).finish()
                MatrixSession(client, sync, paths).also { it.start() }
            }.onFailure { Log.e("AppGraph", "restore failed", it) }.getOrNull()
        }?.also { s ->
            _state.value = AppState.SignedIn(s)
            scope.launch {
                // The app may have come on screen while the client was being built.
                val visible = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                    .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                if (visible && !s.inForeground) {
                    s.inForeground = true
                    s.scope.launch { s.startSync() }
                }
            }
            scope.launch {
                s.authError.collect { soft ->
                    if (!soft) forgetSession("You were signed out. This session was removed or your password changed.")
                }
            }
        }
    }

    /** Signs out on the server, then wipes this device's copy of the account. */
    suspend fun signOut() {
        val s = session ?: return
        s.logout()
        wipe(s)
        _state.value = AppState.SignedOut()
    }

    private suspend fun forgetSession(message: String) {
        val s = session ?: return
        s.close()
        wipe(s)
        _state.value = AppState.SignedOut(message)
    }

    private suspend fun wipe(s: MatrixSession) = withContext(Dispatchers.IO) {
        store.clear()
        s.paths.deleteAll()
    }
}
