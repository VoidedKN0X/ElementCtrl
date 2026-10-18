package com.vknxworks.elementctrl.matrix

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.BackupState
import org.matrix.rustcomponents.sdk.BackupStateListener
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientDelegate
import org.matrix.rustcomponents.sdk.NotificationSettings
import org.matrix.rustcomponents.sdk.ProfileListener
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.RecoveryStateListener
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomListDynamicEntriesController
import org.matrix.rustcomponents.sdk.RoomListEntriesDynamicFilterKind
import org.matrix.rustcomponents.sdk.RoomListEntriesListener
import org.matrix.rustcomponents.sdk.RoomListEntriesUpdate
import org.matrix.rustcomponents.sdk.RoomListEntriesWithDynamicAdaptersResult
import org.matrix.rustcomponents.sdk.RoomListLoadingState
import org.matrix.rustcomponents.sdk.RoomListLoadingStateListener
import org.matrix.rustcomponents.sdk.RoomListService
import org.matrix.rustcomponents.sdk.SyncService
import org.matrix.rustcomponents.sdk.SyncServiceState
import org.matrix.rustcomponents.sdk.SyncServiceStateObserver
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.UserProfile
import org.matrix.rustcomponents.sdk.VerificationState
import org.matrix.rustcomponents.sdk.VerificationStateListener
import uniffi.matrix_sdk_common.BackgroundTaskFailureReason

private const val TAG = "MatrixSession"

/** A signed-in account: its SDK client, the sync loop, the room list and the encryption status. */
class MatrixSession(
    val client: Client,
    private val syncService: SyncService,
    val paths: SessionPaths,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val userId: String = client.userId()
    val deviceId: String = client.deviceId()
    val homeserver: String = client.homeserver()
    val roomListService: RoomListService = syncService.roomListService()

    private val handles = mutableListOf<TaskHandle>()

    private val _syncState = MutableStateFlow(SyncServiceState.IDLE)
    val syncState: StateFlow<SyncServiceState> = _syncState.asStateFlow()

    private val _rooms = MutableStateFlow<List<RoomSummary>>(emptyList())
    val rooms: StateFlow<List<RoomSummary>> = _rooms.asStateFlow()

    private val _roomsLoaded = MutableStateFlow(false)
    val roomsLoaded: StateFlow<Boolean> = _roomsLoaded.asStateFlow()

    private val _profile = MutableStateFlow(UserProfile(userId, null, null, null, null))
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _verification = MutableStateFlow(VerificationState.UNKNOWN)
    val verification: StateFlow<VerificationState> = _verification.asStateFlow()

    private val _recovery = MutableStateFlow(RecoveryState.UNKNOWN)
    val recovery: StateFlow<RecoveryState> = _recovery.asStateFlow()

    private val _backup = MutableStateFlow(BackupState.UNKNOWN)
    val backup: StateFlow<BackupState> = _backup.asStateFlow()

    /** Fires when the server rejects our token (signed out elsewhere, password changed, device removed). */
    private val _authError = MutableSharedFlow<Boolean>(replay = 1)
    val authError: SharedFlow<Boolean> = _authError.asSharedFlow()

    val verificationService by lazy { VerificationService(this) }

    private val listUpdates = Channel<List<RoomListEntriesUpdate>>(Channel.UNLIMITED)
    private var listResult: RoomListEntriesWithDynamicAdaptersResult? = null
    private var listController: RoomListDynamicEntriesController? = null
    private val listMutex = Mutex()
    private var restartJob: Job? = null
    private val _appActive = MutableStateFlow(
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    )
    /**
     * Whether the app is on screen. False when Android started the process only for a background job. Setting it
     * is all that's needed: the sync orchestrator starts and stops syncing to match.
     */
    var inForeground: Boolean
        get() = _appActive.value
        set(value) { _appActive.value = value; syncWake.trySend(Unit) }
    /** Background jobs (the periodic check) that need syncing while the app isn't on screen. */
    private val backgroundHolds = java.util.concurrent.atomic.AtomicInteger(0)
    private val syncWake = Channel<Unit>(Channel.CONFLATED)
    /** Set when signing out: the orchestrator must not start syncing again. */
    @Volatile private var shuttingDown = false
    private val network = NetworkWatch(com.vknxworks.elementctrl.ElementCtrlApp.instance) { syncWake.trySend(Unit) }

    private var notificationSettings: NotificationSettings? = null
    private var notificationClient: org.matrix.rustcomponents.sdk.NotificationClient? = null
    private val notificationMutex = Mutex()

    /**
     * Fetches and decrypts one event for a push notification. Throws when the homeserver can't be reached,
     * so the caller can try again later instead of dropping the notification.
     */
    suspend fun fetchNotification(roomId: String, eventId: String): NotificationFetch {
        val nc = notificationMutex.withLock {
            notificationClient ?: client.notificationClient(
                org.matrix.rustcomponents.sdk.NotificationProcessSetup.SingleProcess(syncService)
            ).also { notificationClient = it }
        }
        return when (val status = nc.getNotification(roomId, eventId)) {
            is org.matrix.rustcomponents.sdk.NotificationStatus.Event -> NotificationFetch.Found(status.item)
            org.matrix.rustcomponents.sdk.NotificationStatus.EventNotFound -> NotificationFetch.NotFound
            // Filtered out by push rules (muted, own message…) or already deleted: nothing to show.
            else -> NotificationFetch.Nothing
        }
    }

    fun start() {
        client.setDelegate(object : ClientDelegate {
            override fun didReceiveAuthError(isSoftLogout: Boolean) {
                _authError.tryEmit(isSoftLogout)
            }
            override fun onBackgroundTaskErrorReport(taskName: String, error: BackgroundTaskFailureReason) {
                Log.w(TAG, "SDK background task $taskName failed: $error")
            }
        })?.let(handles::add)

        handles += syncService.state(object : SyncServiceStateObserver {
            override fun onUpdate(state: SyncServiceState) {
                _syncState.value = state
                syncWake.trySend(Unit)
            }
        })

        val encryption = client.encryption()
        _verification.value = encryption.verificationState()
        _recovery.value = encryption.recoveryState()
        _backup.value = encryption.backupState()
        handles += encryption.verificationStateListener(object : VerificationStateListener {
            override fun onUpdate(status: VerificationState) { _verification.value = status }
        })
        handles += encryption.recoveryStateListener(object : RecoveryStateListener {
            override fun onUpdate(status: RecoveryState) { _recovery.value = status }
        })
        handles += encryption.backupStateListener(object : BackupStateListener {
            override fun onUpdate(status: BackupState) { _backup.value = status }
        })
        handles += client.subscribeToOwnProfile(object : ProfileListener {
            override fun onUpdate(profile: UserProfile) { _profile.value = profile }
        })

        scope.launch {
            runCatching {
                _profile.value = UserProfile(userId, client.displayName(), client.avatarUrl(), null, null)
            }
        }
        network.start()
        restartJob = scope.launch { orchestrateSync() }
        scope.launch { observeRoomList() }
        verificationService.prepare()
    }

    private suspend fun startSync() {
        runCatching { syncService.start() }.onFailure { Log.w(TAG, "start sync failed", it) }
    }

    private suspend fun stopSync() {
        runCatching { syncService.stop() }
    }

    /** Keeps syncing for the duration of [block] even though the app isn't on screen (background checks). */
    suspend fun <T> withBackgroundSync(block: suspend () -> T): T {
        backgroundHolds.incrementAndGet()
        syncWake.trySend(Unit)
        try {
            return block()
        } finally {
            backgroundHolds.decrementAndGet()
            syncWake.trySend(Unit)
        }
    }

    /**
     * The one place that starts and stops syncing, modelled on Element X's SyncOrchestrator. Calls are made one at
     * a time from this loop, so a quick leave-and-return can't end with a stop landing after the start (which left
     * the app idle and disconnected until it was force-stopped). It also:
     * - starts syncing whenever the app is active, there's a network, and the sync service is idle or stopped;
     * - waits 3 seconds before stopping after the app leaves the screen, so quick app switches keep the connection;
     * - restarts a sync service that died (expired session after a long break, server errors) with growing pauses;
     * - kicks a sync service stuck "offline" although Android reports a working network again.
     */
    private suspend fun orchestrateSync() {
        var inactiveSince = 0L
        var runningSince = 0L
        var offlineSince = 0L
        var attempt = 0
        var nextRestartAt = 0L
        while (true) {
            val now = android.os.SystemClock.elapsedRealtime()
            val active = _appActive.value || backgroundHolds.get() > 0
            val online = network.online
            val state = _syncState.value
            if (active) inactiveSince = 0L else if (inactiveSince == 0L) inactiveSince = now
            if (state == SyncServiceState.RUNNING) {
                if (runningSince == 0L) runningSince = now
                // Healthy for a while: forget earlier failures.
                if (now - runningSince > 30_000) attempt = 0
            } else runningSince = 0L
            if (state == SyncServiceState.OFFLINE) { if (offlineSince == 0L) offlineSince = now } else offlineSince = 0L

            when {
                shuttingDown -> return
                !active && (state == SyncServiceState.RUNNING || state == SyncServiceState.OFFLINE) -> {
                    if (now - inactiveSince >= 3_000) stopSync()
                }
                active && online && (state == SyncServiceState.IDLE || state == SyncServiceState.TERMINATED) -> startSync()
                active && online && (state == SyncServiceState.ERROR ||
                    (state == SyncServiceState.OFFLINE && now - offlineSince > 10_000)) && now >= nextRestartAt -> {
                    Log.w(TAG, "sync service is $state, restarting (attempt ${attempt + 1})")
                    if (state == SyncServiceState.OFFLINE) stopSync()
                    startSync()
                    nextRestartAt = now + (1_000L shl attempt.coerceAtMost(5))
                    attempt++
                }
            }
            // Wait for something to change (state, foreground, network), re-checking regularly either way.
            kotlinx.coroutines.withTimeoutOrNull(if (active) 1_000L else 5_000L) { syncWake.receive() }
        }
    }

    private suspend fun observeRoomList() {
        val roomList = runCatching { roomListService.allRooms() }.getOrElse {
            Log.e(TAG, "room list unavailable", it); return
        }
        val loading = roomList.loadingState(object : RoomListLoadingStateListener {
            override fun onUpdate(state: RoomListLoadingState) {
                if (state is RoomListLoadingState.Loaded) _roomsLoaded.value = true
            }
        })
        if (loading.state is RoomListLoadingState.Loaded) _roomsLoaded.value = true
        handles += loading.stateStream

        val result = roomList.entriesWithDynamicAdapters(PAGE_SIZE.toUInt(), object : RoomListEntriesListener {
            override fun onUpdate(roomEntriesUpdate: List<RoomListEntriesUpdate>) {
                listUpdates.trySend(roomEntriesUpdate)
            }
        })
        // One consumer keeps the updates in the order the SDK sent them.
        scope.launch { for (batch in listUpdates) applyUpdates(batch) }
        listResult = result
        handles += result.entriesStream()
        val controller = result.controller()
        listController = controller
        controller.setFilter(
            RoomListEntriesDynamicFilterKind.All(
                listOf(RoomListEntriesDynamicFilterKind.NonLeft, RoomListEntriesDynamicFilterKind.DeduplicateVersions)
            )
        )
    }

    /** Loads another page of rooms once the list is scrolled near its end. */
    fun loadMoreRooms() {
        runCatching { listController?.addOnePage() }
    }

    private suspend fun applyUpdates(updates: List<RoomListEntriesUpdate>) = listMutex.withLock {
        val list = _rooms.value.toMutableList()
        for (u in updates) {
            when (u) {
                is RoomListEntriesUpdate.Append -> list.addAll(u.values.map { it.summary() })
                is RoomListEntriesUpdate.PushBack -> list.add(u.value.summary())
                is RoomListEntriesUpdate.PushFront -> list.add(0, u.value.summary())
                is RoomListEntriesUpdate.Set -> if (u.index.toInt() < list.size) list[u.index.toInt()] = u.value.summary()
                is RoomListEntriesUpdate.Insert -> list.add(u.index.toInt().coerceAtMost(list.size), u.value.summary())
                is RoomListEntriesUpdate.Remove -> if (u.index.toInt() < list.size) list.removeAt(u.index.toInt())
                is RoomListEntriesUpdate.Reset -> { list.clear(); list.addAll(u.values.map { it.summary() }) }
                is RoomListEntriesUpdate.Truncate -> while (list.size > u.length.toInt()) list.removeAt(list.lastIndex)
                RoomListEntriesUpdate.PopBack -> list.removeLastOrNull()
                RoomListEntriesUpdate.PopFront -> list.removeFirstOrNull()
                RoomListEntriesUpdate.Clear -> list.clear()
            }
        }
        _rooms.value = list.distinctBy { it.roomId }
    }

    private suspend fun Room.summary(): RoomSummary = use { it.toSummary() }

    /** Re-reads one row, e.g. after marking it read or changing its notification mode. */
    fun refreshRoom(roomId: String) {
        scope.launch {
            val fresh = getRoom(roomId)?.summary() ?: return@launch
            listMutex.withLock {
                _rooms.value = _rooms.value.map { if (it.roomId == roomId) fresh else it }
            }
        }
    }

    /** The SDK room object. Callers that keep it for a screen's lifetime should close it when done. */
    fun getRoom(roomId: String): Room? =
        runCatching { client.getRoom(roomId) }.getOrNull()
            ?: runCatching { roomListService.room(roomId) }.getOrNull()

    suspend fun subscribeToRoom(roomId: String) {
        runCatching { roomListService.subscribeToRooms(listOf(roomId)) }
    }

    suspend fun notificationSettings(): NotificationSettings =
        notificationSettings ?: client.getNotificationSettings().also { notificationSettings = it }

    suspend fun logout() = withContext(Dispatchers.IO) {
        shuttingDown = true
        runCatching { syncService.stop() }
        runCatching { client.logout() }
        close()
    }

    /** Stops syncing and releases everything without contacting the server (used when the session is already dead). */
    suspend fun shutdown() = withContext(Dispatchers.IO) {
        shuttingDown = true
        runCatching { syncService.stop() }
        close()
    }

    fun close() {
        restartJob?.cancel()
        network.stop()
        handles.forEach { runCatching { it.cancel() } }
        handles.clear()
        runCatching { notificationClient?.close() }
        runCatching { listController?.destroy() }
        runCatching { listResult?.destroy() }
        scope.cancel()
    }

    companion object {
        const val PAGE_SIZE = 200
    }
}

/** RoomListService.setRoomSubscriptions keeps a room's state fully synced while its screen is open. */
private suspend fun RoomListService.subscribeToRooms(ids: List<String>) = setRoomSubscriptions(ids)

/** What fetching a pushed event gave. */
sealed interface NotificationFetch {
    data class Found(val item: org.matrix.rustcomponents.sdk.NotificationItem) : NotificationFetch
    /** The server didn't have it (yet): worth a generic notification so the message isn't missed. */
    data object NotFound : NotificationFetch
    data object Nothing : NotificationFetch
}

/** Follows whether Android has a network with internet, and calls [onChange] when that changes. */
private class NetworkWatch(context: android.content.Context, private val onChange: () -> Unit) {
    private val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
    @Volatile var online: Boolean = true
        private set
    private val callback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
            update(caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET))
        }
        override fun onLost(network: android.net.Network) = update(false)
        override fun onUnavailable() = update(false)
    }

    private fun update(value: Boolean) {
        if (online != value) { online = value; onChange() }
    }

    fun start() {
        online = runCatching {
            cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrNull() ?: true
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
    }

    fun stop() {
        runCatching { cm?.unregisterNetworkCallback(callback) }
    }
}
