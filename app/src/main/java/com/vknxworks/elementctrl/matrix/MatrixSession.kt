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
    /** Whether the app is on screen. False when Android started the process only for the background check. */
    @Volatile var inForeground = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
        .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)

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
                if (state == SyncServiceState.ERROR || state == SyncServiceState.TERMINATED) scheduleRestart()
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
        // In the background the notification worker decides how long to sync.
        if (inForeground) scope.launch { startSync() }
        scope.launch { observeRoomList() }
        verificationService.prepare()
    }

    suspend fun startSync() {
        runCatching { syncService.start() }.onFailure { Log.w(TAG, "start sync failed", it) }
    }

    suspend fun stopSync() {
        runCatching { syncService.stop() }
    }

    private fun scheduleRestart() {
        if (!inForeground) return
        if (restartJob?.isActive == true) return
        restartJob = scope.launch {
            delay(3000)
            if (inForeground) startSync()
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
        runCatching { syncService.stop() }
        runCatching { client.logout() }
        close()
    }

    /** Stops syncing and releases everything without contacting the server (used when the session is already dead). */
    suspend fun shutdown() = withContext(Dispatchers.IO) {
        runCatching { syncService.stop() }
        close()
    }

    fun close() {
        restartJob?.cancel()
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
