package com.vknxworks.elementctrl.matrix

import org.matrix.rustcomponents.sdk.LatestEventValue
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.NotificationRoomInfo
import org.matrix.rustcomponents.sdk.RoomInfo
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.TimelineItemContent
import uniffi.matrix_sdk_base.EncryptionState
import uniffi.matrix_sdk_ui.LatestEventValueLocalState
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

/** Everything the room list needs to draw one row. */
data class RoomSummary(
    val roomId: String,
    val name: String,
    val avatarUrl: String?,
    val isDm: Boolean,
    val isInvite: Boolean,
    val isSpace: Boolean,
    val isFavourite: Boolean,
    val isLowPriority: Boolean,
    val isEncrypted: Boolean,
    val isMarkedUnread: Boolean,
    val unreadMessages: Long,
    val unreadNotifications: Long,
    val unreadMentions: Long,
    val notificationMode: RoomNotificationMode?,
    val lastMessage: String?,
    val lastSender: String?,
    val lastIsOwn: Boolean,
    val lastTimestamp: Long?,
    val lastSending: Boolean,
    val lastFailed: Boolean,
    val inviterName: String?,
    val dmUserId: String?,
    val canonicalAlias: String?,
    val topic: String?,
    val memberCount: Long,
    /** A call is going on in the room. */
    val hasCall: Boolean = false,
) {
    val isMuted get() = notificationMode == RoomNotificationMode.MUTE
    val hasUnread get() = isMarkedUnread || unreadMessages > 0 || unreadNotifications > 0
    val searchText get() = "$name ${canonicalAlias.orEmpty()} ${dmUserId.orEmpty()}".lowercase()
}

fun RoomInfo.displayNameOrId(): String = displayName?.takeIf { it.isNotBlank() } ?: canonicalAlias ?: id

/**
 * A one-to-one chat: marked as a direct chat and with no more than two people in it.
 *
 * The SDK's own isDm also counts people who were invited but never joined, so a single stale invite turned a chat
 * into a "room": calls from it then only showed a "call started" message on the other side instead of ringing.
 * Like Element Desktop, only the people who have joined count here.
 */
fun RoomInfo.isOneToOne(): Boolean = isDm || isDirect && joinedMembersCount <= 2uL

/** The same rule for a room as a push notification describes it. */
fun NotificationRoomInfo.isOneToOne(): Boolean = isDm || isDirect && joinedMembersCount <= 2uL

/** Reads the room's current info and latest event. Safe to call for every list update. */
suspend fun Room.toSummary(): RoomSummary {
    val info = roomInfo()
    val isDm = info.isOneToOne()
    val dmHero = if (isDm) info.heroes.firstOrNull() else null
    var lastMessage: String? = null
    var lastSender: String? = null
    var lastOwn = false
    var lastTs: Long? = null
    var sending = false
    var failed = false
    var inviterName: String? = info.inviter?.let { it.displayName ?: it.userId.localpart() }

    fun describe(content: TimelineItemContent, senderName: String, own: Boolean): String {
        val text = content.previewText()
        return when (content) {
            is TimelineItemContent.MsgLike -> text
            // State events read as sentences ("Ana joined"), so fill in who did it.
            is TimelineItemContent.State -> stateText(if (own) Strings.str(R.string.you_title) else senderName, content.content)
            is TimelineItemContent.ProfileChange -> profileChangeText(if (own) Strings.str(R.string.you_title) else senderName, content.displayName, content.prevDisplayName, content.avatarUrl, content.prevAvatarUrl)
            else -> text
        }
    }

    when (val latest = latestEvent()) {
        is LatestEventValue.Remote -> {
            val name = latest.profile.nameOr(latest.sender)
            lastMessage = describe(latest.content, name, latest.isOwn)
            lastSender = if (latest.content is TimelineItemContent.MsgLike) (if (latest.isOwn) Strings.str(R.string.you_title) else name) else null
            lastOwn = latest.isOwn
            lastTs = latest.timestamp.toLong()
        }
        is LatestEventValue.Local -> {
            lastMessage = describe(latest.content, Strings.str(R.string.you_title), true)
            lastSender = Strings.str(R.string.you_title)
            lastOwn = true
            lastTs = latest.timestamp.toLong()
            sending = latest.state == LatestEventValueLocalState.IS_SENDING
            failed = latest.state == LatestEventValueLocalState.CANNOT_BE_SENT
        }
        is LatestEventValue.RemoteInvite -> {
            inviterName = latest.inviterProfile.nameOr(latest.inviter ?: "")
                .takeIf { it.isNotBlank() } ?: inviterName
            lastTs = latest.timestamp.toLong()
        }
        is LatestEventValue.None -> Unit
    }

    return RoomSummary(
        roomId = info.id,
        name = info.displayNameOrId(),
        avatarUrl = info.avatarUrl ?: dmHero?.avatarUrl,
        isDm = isDm,
        isInvite = info.membership == Membership.INVITED,
        isSpace = info.isSpace,
        isFavourite = info.isFavourite,
        isLowPriority = info.isLowPriority,
        isEncrypted = info.encryptionState == EncryptionState.ENCRYPTED,
        isMarkedUnread = info.isMarkedUnread,
        unreadMessages = info.numUnreadMessages.toLong(),
        unreadNotifications = info.numUnreadNotifications.toLong(),
        unreadMentions = info.numUnreadMentions.toLong(),
        notificationMode = info.cachedUserDefinedNotificationMode,
        lastMessage = lastMessage,
        lastSender = lastSender,
        lastIsOwn = lastOwn,
        lastTimestamp = lastTs,
        lastSending = sending,
        lastFailed = failed,
        inviterName = inviterName,
        dmUserId = dmHero?.userId,
        canonicalAlias = info.canonicalAlias,
        topic = info.topic,
        memberCount = info.joinedMembersCount.toLong(),
        hasCall = info.hasRoomCall,
    )
}
