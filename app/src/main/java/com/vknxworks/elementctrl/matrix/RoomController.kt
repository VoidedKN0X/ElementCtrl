package com.vknxworks.elementctrl.matrix

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.JoinRule
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.MembershipState
import org.matrix.rustcomponents.sdk.PowerLevel
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomHistoryVisibility
import org.matrix.rustcomponents.sdk.RoomInfo
import org.matrix.rustcomponents.sdk.RoomInfoListener
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TypingNotificationsListener
import uniffi.matrix_sdk_base.EncryptionState
import uniffi.ruma_events.StateEventType
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

/** The parts of a room's state the room and details screens show. */
data class RoomHeader(
    val id: String,
    val name: String,
    val topic: String?,
    val avatarUrl: String?,
    val alias: String?,
    val isDm: Boolean,
    val isEncrypted: Boolean,
    val isPublic: Boolean,
    val isFavourite: Boolean,
    val isLowPriority: Boolean,
    val isMarkedUnread: Boolean,
    val membership: Membership,
    val inviterName: String?,
    val joinedMembers: Long,
    val invitedMembers: Long,
    val pinnedEventIds: List<String>,
    val historyVisibility: String,
    val joinRule: String,
    val notificationMode: RoomNotificationMode?,
    val dmUserId: String?,
    val successorRoomId: String?,
    /** A MatrixRTC call (Element Call) is going on in the room. */
    val hasCall: Boolean = false,
    /** How many people are in it. */
    val callMembers: Int = 0,
    /** You're in it (on any of your devices). */
    val inCall: Boolean = false,
)

data class MemberInfo(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val powerLevel: Long,
    val membership: String,
    val isIgnored: Boolean,
) {
    val name get() = displayName?.takeIf { it.isNotBlank() } ?: userId.localpart()
    val role get() = when {
        powerLevel >= 150 -> Strings.str(R.string.owner)
        powerLevel >= 100 -> Strings.str(R.string.admin)
        powerLevel >= 50 -> Strings.str(R.string.moderator)
        else -> null
    }
}

/** What the signed-in user may do in a room, from its power levels. */
data class Permissions(
    val canSend: Boolean = true,
    val canInvite: Boolean = false,
    val canKick: Boolean = false,
    val canBan: Boolean = false,
    val canRedactOthers: Boolean = false,
    val canEditName: Boolean = false,
    val canEditTopic: Boolean = false,
    val canEditAvatar: Boolean = false,
    val canPin: Boolean = false,
    val canMentionRoom: Boolean = false,
)

/** Live room state, typing users, members and room-level actions for an open room. */
class RoomController(val session: MatrixSession, val room: Room) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handles = mutableListOf<TaskHandle>()
    val roomId: String = room.id()

    private val _header = MutableStateFlow<RoomHeader?>(null)
    val header: StateFlow<RoomHeader?> = _header.asStateFlow()

    private val _typing = MutableStateFlow<List<String>>(emptyList())
    val typing: StateFlow<List<String>> = _typing.asStateFlow()

    private val _members = MutableStateFlow<List<MemberInfo>>(emptyList())
    val members: StateFlow<List<MemberInfo>> = _members.asStateFlow()

    private val _permissions = MutableStateFlow(Permissions())
    val permissions: StateFlow<Permissions> = _permissions.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var typingJob: Job? = null
    private var isTyping = false

    fun start() {
        handles += room.subscribeToRoomInfoUpdates(object : RoomInfoListener {
            override fun call(roomInfo: RoomInfo) {
                scope.launch { update(roomInfo) }
            }
        })
        handles += room.subscribeToTypingNotifications(object : TypingNotificationsListener {
            override fun call(typingUserIds: List<String>) {
                scope.launch {
                    _typing.value = typingUserIds.filter { it != session.userId }.map { id ->
                        runCatching { room.memberDisplayName(id) }.getOrNull() ?: id.localpart()
                    }
                }
            }
        })
        scope.launch {
            session.subscribeToRoom(roomId)
            runCatching { update(room.roomInfo()) }
        }
    }

    fun close() {
        handles.forEach { runCatching { it.cancel() } }
        handles.clear()
        if (isTyping) scope.launch { runCatching { room.typingNotice(false) } }
        scope.launch {
            delay(200)
            scope.cancel()
        }
    }

    private suspend fun update(info: RoomInfo) {
        val isDm = info.isOneToOne()
        val notif = runCatching {
            session.notificationSettings().getUserDefinedRoomNotificationMode(info.id)
        }.getOrNull()
        _header.value = RoomHeader(
            id = info.id,
            name = info.displayNameOrId(),
            topic = info.topic,
            avatarUrl = info.avatarUrl ?: if (isDm) info.heroes.firstOrNull()?.avatarUrl else null,
            alias = info.canonicalAlias,
            isDm = isDm,
            isEncrypted = info.encryptionState == EncryptionState.ENCRYPTED,
            isPublic = info.isPublic == true,
            isFavourite = info.isFavourite,
            isLowPriority = info.isLowPriority,
            isMarkedUnread = info.isMarkedUnread,
            membership = info.membership,
            inviterName = info.inviter?.let { it.displayName ?: it.userId },
            joinedMembers = info.joinedMembersCount.toLong(),
            invitedMembers = info.invitedMembersCount.toLong(),
            pinnedEventIds = info.pinnedEventIds,
            historyVisibility = when (info.historyVisibility) {
                is RoomHistoryVisibility.WorldReadable -> Strings.str(R.string.anyone)
                is RoomHistoryVisibility.Shared -> Strings.str(R.string.members_since_they_could_read)
                is RoomHistoryVisibility.Invited -> Strings.str(R.string.members_since_they_were_invited)
                is RoomHistoryVisibility.Joined -> Strings.str(R.string.members_since_they_joined)
                is RoomHistoryVisibility.Custom -> Strings.str(R.string.custom)
            },
            joinRule = when (info.joinRule) {
                is JoinRule.Public -> Strings.str(R.string.anyone_can_join)
                is JoinRule.Invite -> Strings.str(R.string.invite_only)
                is JoinRule.Knock -> Strings.str(R.string.ask_to_join)
                is JoinRule.Restricted, is JoinRule.KnockRestricted -> Strings.str(R.string.space_members)
                else -> Strings.str(R.string.private_text)
            },
            notificationMode = notif,
            dmUserId = if (isDm) info.heroes.firstOrNull()?.userId else null,
            successorRoomId = info.successorRoom?.roomId,
            hasCall = info.hasRoomCall,
            callMembers = info.activeRoomCallParticipants.distinct().size,
            inCall = session.userId in info.activeRoomCallParticipants,
        )
        runCatching {
            val pl = room.getPowerLevels()
            _permissions.value = Permissions(
                canSend = pl.canOwnUserSendMessage(uniffi.ruma_events.MessageLikeEventType.RoomMessage),
                canInvite = pl.canOwnUserInvite(),
                canKick = pl.canOwnUserKick(),
                canBan = pl.canOwnUserBan(),
                canRedactOthers = pl.canOwnUserRedactOther(),
                canEditName = pl.canOwnUserSendState(StateEventType.RoomName),
                canEditTopic = pl.canOwnUserSendState(StateEventType.RoomTopic),
                canEditAvatar = pl.canOwnUserSendState(StateEventType.RoomAvatar),
                canPin = pl.canOwnUserPinUnpin(),
                canMentionRoom = pl.canOwnUserTriggerRoomNotification(),
            )
        }
    }

    fun loadMembers() {
        scope.launch {
            runCatching {
                val iterator = room.members()
                val out = ArrayList<MemberInfo>()
                while (true) {
                    val chunk = iterator.nextChunk(200u) ?: break
                    if (chunk.isEmpty()) break
                    chunk.forEach { m ->
                        out += MemberInfo(
                            userId = m.userId,
                            displayName = m.displayName,
                            avatarUrl = m.avatarUrl,
                            powerLevel = when (val p = m.powerLevel) {
                                is PowerLevel.Infinite -> 1000L
                                is PowerLevel.Value -> p.value
                            },
                            membership = when (m.membership) {
                                is MembershipState.Join -> "join"
                                is MembershipState.Invite -> "invite"
                                is MembershipState.Ban -> "ban"
                                is MembershipState.Knock -> "knock"
                                is MembershipState.Leave -> "leave"
                                is MembershipState.Custom -> "custom"
                            },
                            isIgnored = m.isIgnored,
                        )
                    }
                }
                _members.value = out.sortedWith(compareByDescending<MemberInfo> { it.powerLevel }.thenBy { it.name.lowercase() })
            }.onFailure { _error.value = it.userMessage() }
        }
    }

    /** Sends "typing…" while composing, and clears it after a few idle seconds. */
    fun onComposerChanged(text: String) {
        val nowTyping = text.isNotBlank()
        typingJob?.cancel()
        typingJob = scope.launch {
            if (nowTyping != isTyping) {
                isTyping = nowTyping
                runCatching { room.typingNotice(nowTyping) }
            }
            if (nowTyping) {
                delay(5000)
                isTyping = false
                runCatching { room.typingNotice(false) }
            }
        }
    }

    fun clearError() { _error.value = null }

    private fun act(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure { _error.value = it.userMessage() } }
    }

    fun join(onDone: () -> Unit = {}) = act { room.join(); withContext(Dispatchers.Main) { onDone() } }
    fun leave(onDone: () -> Unit) = act { room.leave(); withContext(Dispatchers.Main) { onDone() } }
    fun forget(onDone: () -> Unit) = act { room.leave(); runCatching { room.forget() }; withContext(Dispatchers.Main) { onDone() } }
    fun setName(name: String) = act { room.setName(name) }
    fun setTopic(topic: String) = act { room.setTopic(topic) }
    fun setFavourite(on: Boolean) = act { room.setIsFavourite(on, null); session.refreshRoom(roomId) }
    fun setLowPriority(on: Boolean) = act { room.setIsLowPriority(on, null); session.refreshRoom(roomId) }
    fun setUnread(on: Boolean) = act { room.setUnreadFlag(on); session.refreshRoom(roomId) }
    fun invite(userId: String) = act { room.inviteUserById(userId.trim()) }
    fun kick(userId: String, reason: String?) = act { room.kickUser(userId, reason); loadMembers() }
    fun ban(userId: String, reason: String?) = act { room.banUser(userId, reason); loadMembers() }
    fun unban(userId: String) = act { room.unbanUser(userId, null); loadMembers() }
    fun enableEncryption() = act { room.enableEncryption() }
    fun report(eventId: String, reason: String?) = act { room.reportContent(eventId, reason) }
    fun ignore(userId: String) = act { session.client.ignoreUser(userId) }

    fun setNotificationMode(mode: RoomNotificationMode?) = act {
        val settings = session.notificationSettings()
        if (mode == null) settings.restoreDefaultRoomNotificationMode(roomId) else settings.setRoomNotificationMode(roomId, mode)
        update(room.roomInfo())
        session.refreshRoom(roomId)
    }

    fun setAvatar(context: Context, uri: Uri) = act {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error(Strings.str(R.string.couldn_t_read_the_picture))
        room.uploadAvatar(mime, bytes, null)
    }

    // region drafts

    /** The unsent message for this chat (or thread), kept by the SDK in the encrypted database. */
    suspend fun loadDraft(threadRoot: String?): org.matrix.rustcomponents.sdk.ComposerDraft? =
        runCatching { room.loadComposerDraft(threadRoot) }.getOrNull()

    /** Saves the draft on the session scope, so it still happens while the screen is closing. */
    fun saveDraft(text: String, replyTo: String?, editOf: String?, threadRoot: String?) {
        val drafts = com.vknxworks.elementctrl.ElementCtrlApp.graph.drafts
        session.scope.launch {
            runCatching {
                if (text.isBlank()) {
                    room.clearComposerDraft(threadRoot)
                } else {
                    val type = when {
                        editOf != null -> org.matrix.rustcomponents.sdk.ComposerDraftType.Edit(editOf)
                        replyTo != null -> org.matrix.rustcomponents.sdk.ComposerDraftType.Reply(replyTo)
                        else -> org.matrix.rustcomponents.sdk.ComposerDraftType.NewMessage
                    }
                    room.saveComposerDraft(org.matrix.rustcomponents.sdk.ComposerDraft(text, null, type, emptyList()), threadRoot)
                }
            }
            // Edits aren't shown as drafts in the chat list.
            if (threadRoot == null) drafts.set(roomId, if (editOf != null) null else text)
        }
    }

    // endregion

    suspend fun permalink(): String? = runCatching { room.matrixToPermalink() }.getOrNull()
    suspend fun eventPermalink(eventId: String): String? = runCatching { room.matrixToEventPermalink(eventId) }.getOrNull()
}
