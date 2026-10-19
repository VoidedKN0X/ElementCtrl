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
    val drafts = com.vknxworks.elementctrl.data.Drafts(context)
    val lock = com.vknxworks.elementctrl.data.LockSettings(context)
    val store = SessionStore(context)
    val factory = ClientFactory(context, store)
    val auth = AuthService(this)
    val redirectScheme: String = context.getString(R.string.login_redirect_scheme)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val telemetry = com.vknxworks.elementctrl.telemetry.Telemetry(context, prefs, scope)

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    val session: MatrixSession? get() = (_state.value as? AppState.SignedIn)?.session

    private val mutex = Mutex()

    /** Opens the saved account, if any. Runs once at app start. */
    fun boot() {
        scope.launch {
            val stored = withContext(Dispatchers.IO) { store.load() }
            // Databases left behind by a sign-in that never finished (or an older account) are removed.
            // (Skipped when a saved session exists but couldn't be read, so a passing Keystore hiccup loses nothing.)
            withContext(Dispatchers.IO) {
                runCatching {
                    if (stored != null) removeOrphanSessions(stored.directory)
                    else if (!store.hasSaved()) {
                        // Signed out: finish a sign-out wipe that was cut short (e.g. the app was closed halfway),
                        // so no drafts, decrypted attachments or notification state outlive the account.
                        removeOrphanSessions(null)
                        wipeLocalLeftovers()
                    }
                    Unit
                }
            }
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
            s.onStuck = { reason -> scope.launch { onSessionStuck(s, reason) } }
            _state.value = AppState.SignedIn(s)
            com.vknxworks.elementctrl.notify.Push.refresh(context)
            scope.launch {
                // The app may have come on screen while the client was being built.
                val visible = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                    .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                if (visible && !s.inForeground) s.inForeground = true
            }
            scope.launch {
                s.authError.collect { soft ->
                    if (!soft) forgetSession("You were signed out. This session was removed or your password changed.")
                }
            }
        }
    }

    /**
     * The sync couldn't be brought back by restarting it. If the homeserver answers and the app is on screen,
     * reload the app (what a force stop does), at most once every 10 minutes so a server problem can't cause a loop.
     * Otherwise the chat list's banner offers a manual Reconnect.
     */
    private suspend fun onSessionStuck(s: MatrixSession, reason: String) {
        if (!s.inForeground) return
        val reachable = withContext(Dispatchers.IO) { serverAnswers(s.homeserver) }
        ConnectionLog.log("homeserver ${if (reachable) "answers" else "doesn't answer"}")
        if (!reachable) return
        val p = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
        val last = p.getLong("auto_reload_at", 0)
        if (System.currentTimeMillis() - last < 10 * 60_000) {
            ConnectionLog.log("not reloading automatically: already did in the last 10 minutes")
            return
        }
        p.edit().putLong("auto_reload_at", System.currentTimeMillis()).commit()
        ConnectionLog.log("reloading the app to reconnect ($reason)")
        com.vknxworks.elementctrl.AppRestart.restart(context, com.vknxworks.elementctrl.notify.OpenRoom.id)
    }

    private fun serverAnswers(homeserver: String): Boolean = runCatching {
        val conn = (java.net.URL(homeserver.trimEnd('/') + "/_matrix/client/versions").openConnection() as java.net.HttpURLConnection).apply {
            instanceFollowRedirects = false
            useCaches = false
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        try { conn.responseCode in 200..299 } finally { conn.disconnect() }
    }.getOrDefault(false)

    /** Signs out on the server, then wipes this device's copy of the account. */
    suspend fun signOut() {
        val s = session ?: return
        com.vknxworks.elementctrl.notify.Push.disable(context, s)
        s.logout()
        wipe(s)
        _state.value = AppState.SignedOut()
    }

    private suspend fun forgetSession(message: String) {
        val s = session ?: return
        // The token is already dead, so the pusher can't be removed on the server; drop the local registration.
        runCatching { com.vknxworks.elementctrl.notify.Push.disable(context, null) }
        // Stop syncing first, so nothing writes new files into the folders that are about to be deleted.
        s.shutdown()
        wipe(s)
        _state.value = AppState.SignedOut(message)
    }

    /**
     * Removes everything this device knew about the account: the saved session and keys, the SDK's databases
     * (messages, crypto store, search index, media cache), decrypted attachments and voice messages, the draft index,
     * notification state, and pictures held in memory.
     */
    private suspend fun wipe(s: MatrixSession) = withContext(Dispatchers.IO) {
        // The saved session goes first, so the account can never be reopened from a half-wiped device. Anything
        // after it that is cut short (app closed mid-wipe) is finished at the next start (see boot()).
        store.clear()
        runCatching { s.paths.deleteAll() }
        runCatching { removeOrphanSessions(keep = null) }
        wipeLocalLeftovers()
        runCatching { com.vknxworks.elementctrl.notify.Notifier.schedule(context, false) }
        runCatching { LinkPreviews.clear() }
        withContext(Dispatchers.Main) {
            runCatching { com.vknxworks.elementctrl.ui.room.AudioPlayer.stop() }
            runCatching { coil3.SingletonImageLoader.get(context).memoryCache?.clear() }
        }
    }

    /** Account data kept outside the SDK's folders: drafts, decrypted/outgoing files and notification state. */
    private fun wipeLocalLeftovers() {
        runCatching { drafts.clearAll() }
        runCatching { MediaFiles.wipeAll(context) }
        runCatching { com.vknxworks.elementctrl.notify.Notifier.forgetAll(context) }
        runCatching { com.vknxworks.elementctrl.notify.PushNotifications.clearAll(context) }
        runCatching { com.vknxworks.elementctrl.notify.PushQueue.clear(context) }
        runCatching { ConnectionLog.clear() }
    }

    /** Deletes every account folder under files/sessions and cache/sessions except [keep]. */
    private fun removeOrphanSessions(keep: String?) {
        listOf(java.io.File(context.filesDir, "sessions"), java.io.File(context.cacheDir, "sessions")).forEach { root ->
            root.listFiles()?.forEach { dir -> if (dir.name != keep) dir.deleteRecursively() }
        }
    }
}
