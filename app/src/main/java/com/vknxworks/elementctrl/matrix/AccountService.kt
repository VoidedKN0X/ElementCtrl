package com.vknxworks.elementctrl.matrix

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.matrix.rustcomponents.sdk.AuthData
import org.matrix.rustcomponents.sdk.AuthDataPasswordDetails
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.CrossSigningResetAuthType
import org.matrix.rustcomponents.sdk.EnableRecoveryProgress
import org.matrix.rustcomponents.sdk.EnableRecoveryProgressListener
import org.matrix.rustcomponents.sdk.IdentityResetHandle
import org.matrix.rustcomponents.sdk.RoomDescription
import org.matrix.rustcomponents.sdk.RoomDirectorySearch
import org.matrix.rustcomponents.sdk.RoomDirectorySearchEntriesListener
import org.matrix.rustcomponents.sdk.RoomDirectorySearchEntryUpdate
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.RoomPreset
import org.matrix.rustcomponents.sdk.RoomVisibility
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.UserProfile
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

/** Account-level actions: profile, people search, creating and joining rooms, and encryption recovery. */
class AccountService(private val session: MatrixSession) {
    private val client get() = session.client

    suspend fun setDisplayName(name: String) = io { client.setDisplayName(name) }

    suspend fun setAvatar(context: Context, uri: Uri) = io {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error(Strings.str(R.string.couldn_t_read_the_picture))
        client.uploadAvatar(mime, bytes)
    }

    suspend fun removeAvatar() = io { client.removeAvatar() }

    /** The web page for managing devices, password and sessions on OAuth servers (null on password-only servers). */
    suspend fun accountManagementUrl(): String? = io { runCatching { client.accountUrl(null) }.getOrNull() }

    suspend fun ignoredUsers(): List<String> = io { client.ignoredUsers() }
    suspend fun unignore(userId: String) = io { client.unignoreUser(userId) }
    suspend fun ignore(userId: String) = io { client.ignoreUser(userId) }

    /** Directory search, plus an exact lookup when a full Matrix ID was typed. */
    suspend fun searchUsers(term: String): List<UserProfile> = io {
        val results = runCatching { client.searchUsers(term, 30u).results }.getOrDefault(emptyList()).toMutableList()
        if (term.startsWith("@") && term.contains(":") && results.none { it.userId == term }) {
            val profile = runCatching { client.getProfile(term) }.getOrNull() ?: UserProfile(term, null, null, null, null)
            results.add(0, profile)
        }
        results.filter { it.userId != session.userId }
    }

    suspend fun profile(userId: String): UserProfile? = io { runCatching { client.getProfile(userId) }.getOrNull() }

    /** Opens the existing direct chat with someone, or creates an encrypted one and invites them. */
    suspend fun startDm(userId: String): String = io {
        client.getDmRoom(userId)?.use { return@io it.id() }
        client.createRoom(
            CreateRoomParameters(
                name = null,
                isEncrypted = true,
                isDirect = true,
                visibility = RoomVisibility.Private,
                preset = RoomPreset.TRUSTED_PRIVATE_CHAT,
                invite = listOf(userId),
            )
        ).also { awaitRoom(it) }
    }

    /** Waits (briefly) for a room we just created to come back through sync, so its screen can open it. */
    private suspend fun awaitRoom(roomId: String) {
        withTimeoutOrNull(15_000) { runCatching { client.awaitRoomRemoteEcho(roomId).close() } }
    }

    suspend fun createRoom(name: String, topic: String?, isPublic: Boolean, encrypted: Boolean, alias: String?, invite: List<String>): String = io {
        client.createRoom(
            CreateRoomParameters(
                name = name,
                topic = topic?.takeIf { it.isNotBlank() },
                isEncrypted = encrypted && !isPublic,
                isDirect = false,
                visibility = if (isPublic) RoomVisibility.Public else RoomVisibility.Private,
                preset = if (isPublic) RoomPreset.PUBLIC_CHAT else RoomPreset.PRIVATE_CHAT,
                invite = invite.takeIf { it.isNotEmpty() },
                canonicalAlias = alias?.takeIf { isPublic && it.isNotBlank() },
            )
        ).also { awaitRoom(it) }
    }

    /** Joins by room ID (!abc:server) or address (#room:server). Returns the room ID. */
    suspend fun join(idOrAlias: String, via: List<String> = emptyList()): String = io {
        client.joinRoomByIdOrAlias(idOrAlias.trim(), via).use { it.id() }
    }

    // region encryption recovery

    /** Turns on key storage and returns the new recovery key to show once. */
    suspend fun enableRecovery(passphrase: String?, onProgress: (String) -> Unit): String = io {
        client.encryption().enableRecovery(
            waitForBackupsToUpload = false,
            passphrase = passphrase?.takeIf { it.isNotBlank() },
            progressListener = object : EnableRecoveryProgressListener {
                override fun onUpdate(status: EnableRecoveryProgress) {
                    onProgress(
                        when (status) {
                            is EnableRecoveryProgress.Starting -> Strings.str(R.string.starting)
                            is EnableRecoveryProgress.CreatingBackup -> Strings.str(R.string.creating_key_backup)
                            is EnableRecoveryProgress.CreatingRecoveryKey -> Strings.str(R.string.creating_recovery_key)
                            is EnableRecoveryProgress.BackingUp -> Strings.str(R.string.backing_up_keys, status.backedUpCount, status.totalCount)
                            is EnableRecoveryProgress.RoomKeyUploadError -> Strings.str(R.string.some_keys_couldn_t_be_uploaded_yet)
                            is EnableRecoveryProgress.Done -> Strings.str(R.string.lock_done)
                        }
                    )
                }
            },
        )
    }

    suspend fun recover(key: String) = io { client.encryption().recover(key.trim()) }
    suspend fun resetRecoveryKey(): String = io { client.encryption().resetRecoveryKey() }
    suspend fun disableRecovery() = io { client.encryption().disableRecovery() }

    /**
     * Starts resetting the cryptographic identity (when no other device or recovery key is available).
     * Password servers need the password; OAuth servers need approval in the browser first.
     */
    suspend fun beginIdentityReset(): IdentityReset? = io {
        client.encryption().resetIdentity()?.let { IdentityReset(it, session.userId) }
    }

    // endregion

    // region notification defaults

    suspend fun defaultMode(oneToOne: Boolean): RoomNotificationMode = io {
        session.notificationSettings().getDefaultRoomNotificationMode(isEncrypted = true, isOneToOne = oneToOne)
    }

    suspend fun setDefaultMode(oneToOne: Boolean, mode: RoomNotificationMode) = io {
        val s = session.notificationSettings()
        s.setDefaultRoomNotificationMode(true, oneToOne, mode)
        s.setDefaultRoomNotificationMode(false, oneToOne, mode)
    }

    suspend fun mentionsEnabled(): Boolean = io { session.notificationSettings().isUserMentionEnabled() }
    suspend fun setMentionsEnabled(on: Boolean) = io {
        val s = session.notificationSettings()
        s.setUserMentionEnabled(on)
        s.setRoomMentionEnabled(on)
    }

    suspend fun invitesEnabled(): Boolean = io { session.notificationSettings().isInviteForMeEnabled() }
    suspend fun setInvitesEnabled(on: Boolean) = io { session.notificationSettings().setInviteForMeEnabled(on) }

    // endregion

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}

class IdentityReset(private val handle: IdentityResetHandle, private val userId: String) {
    /** For OAuth servers: the page where the reset must be approved before calling [confirm]. */
    val approvalUrl: String? = (handle.authType() as? CrossSigningResetAuthType.OAuth)?.info?.approvalUrl
    val needsPassword: Boolean = handle.authType() is CrossSigningResetAuthType.Uiaa

    suspend fun confirm(password: String?) = withContext(Dispatchers.IO) {
        when (val type = handle.authType()) {
            is CrossSigningResetAuthType.Uiaa -> handle.reset(
                AuthData.Password(AuthDataPasswordDetails(identifier = userId, password = password.orEmpty(), session = type.info.session))
            )
            is CrossSigningResetAuthType.OAuth -> handle.reset(AuthData.OAuth(type.info.session))
        }
    }

    suspend fun cancel() = withContext(Dispatchers.IO) { runCatching { handle.cancel() } }
}

/** Public room directory search on the home server (or another server). */
class DirectorySearch(session: MatrixSession) {
    private val search: RoomDirectorySearch = session.client.roomDirectorySearch()
    private var handle: TaskHandle? = null
    private val items = ArrayList<RoomDescription>()
    private val _results = MutableStateFlow<List<RoomDescription>>(emptyList())
    val results: StateFlow<List<RoomDescription>> = _results.asStateFlow()

    suspend fun query(text: String?, server: String?) = withContext(Dispatchers.IO) {
        if (handle == null) {
            handle = search.results(object : RoomDirectorySearchEntriesListener {
                override fun onUpdate(roomEntriesUpdate: List<RoomDirectorySearchEntryUpdate>) {
                    synchronized(items) {
                        for (u in roomEntriesUpdate) when (u) {
                            is RoomDirectorySearchEntryUpdate.Append -> items.addAll(u.values)
                            is RoomDirectorySearchEntryUpdate.PushBack -> items.add(u.value)
                            is RoomDirectorySearchEntryUpdate.PushFront -> items.add(0, u.value)
                            is RoomDirectorySearchEntryUpdate.Insert -> items.add(u.index.toInt().coerceAtMost(items.size), u.value)
                            is RoomDirectorySearchEntryUpdate.Set -> if (u.index.toInt() < items.size) items[u.index.toInt()] = u.value
                            is RoomDirectorySearchEntryUpdate.Remove -> if (u.index.toInt() < items.size) items.removeAt(u.index.toInt())
                            is RoomDirectorySearchEntryUpdate.Truncate -> while (items.size > u.length.toInt()) items.removeAt(items.lastIndex)
                            is RoomDirectorySearchEntryUpdate.Reset -> { items.clear(); items.addAll(u.values) }
                            RoomDirectorySearchEntryUpdate.Clear -> items.clear()
                            RoomDirectorySearchEntryUpdate.PopBack -> items.removeLastOrNull()
                            RoomDirectorySearchEntryUpdate.PopFront -> items.removeFirstOrNull()
                        }
                        _results.value = ArrayList(items)
                    }
                }
            })
        }
        search.search(text?.takeIf { it.isNotBlank() }, 30u, server?.takeIf { it.isNotBlank() })
    }

    suspend fun more() = withContext(Dispatchers.IO) { runCatching { if (!search.isAtLastPage()) search.nextPage() } }

    fun close() { runCatching { handle?.cancel() } }
}
