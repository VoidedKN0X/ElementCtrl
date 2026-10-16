package com.vknxworks.elementctrl.matrix

import org.matrix.rustcomponents.sdk.MembershipChange
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.OtherState
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.TimelineItemContent

/** Display name from a profile, falling back to the localpart of the Matrix ID. */
fun ProfileDetails.nameOr(userId: String): String =
    (this as? ProfileDetails.Ready)?.displayName?.takeIf { it.isNotBlank() } ?: userId.localpart()

fun ProfileDetails.avatarOrNull(): String? = (this as? ProfileDetails.Ready)?.avatarUrl

fun String.localpart(): String = removePrefix("@").substringBefore(':')

/** One-line text for a message, used in the room list, replies and notifications. */
fun TimelineItemContent.previewText(): String = when (this) {
    is TimelineItemContent.MsgLike -> when (val kind = content.kind) {
        is MsgLikeKind.Message -> kind.content.msgType.previewText(kind.content.body)
        is MsgLikeKind.Sticker -> "Sticker: ${kind.body}"
        is MsgLikeKind.Poll -> "📊 ${kind.question}"
        is MsgLikeKind.Redacted -> "Message deleted"
        is MsgLikeKind.UnableToDecrypt -> "🔒 Waiting for this message"
        is MsgLikeKind.LiveLocation -> "📍 Live location"
        is MsgLikeKind.Other -> "Unsupported event"
    }
    is TimelineItemContent.RoomMembership -> membershipText(userDisplayName ?: userId.localpart(), change)
    is TimelineItemContent.ProfileChange -> profileChangeText("Someone", displayName, prevDisplayName, avatarUrl, prevAvatarUrl)
    is TimelineItemContent.State -> stateText("Someone", content)
    is TimelineItemContent.CallInvite -> "📞 Call started"
    is TimelineItemContent.RtcNotification -> "📞 Call"
    is TimelineItemContent.FailedToParseMessageLike -> "Unsupported message"
    is TimelineItemContent.FailedToParseState -> "Unsupported room event"
}

fun MessageType.previewText(body: String): String = when (this) {
    is MessageType.Image -> "🖼 " + (content.caption ?: "Image")
    is MessageType.Video -> "🎬 " + (content.caption ?: "Video")
    is MessageType.Audio -> if (content.voice != null) "🎤 Voice message" else "🎵 " + (content.caption ?: content.filename)
    is MessageType.File -> "📎 " + (content.caption ?: content.filename)
    is MessageType.Location -> "📍 Location"
    is MessageType.Gallery -> "🖼 " + content.body.ifBlank { "Gallery" }
    is MessageType.Emote -> "* " + content.body
    is MessageType.Notice -> content.body
    is MessageType.Text -> content.body
    is MessageType.Other -> body
}

fun membershipText(name: String, change: MembershipChange?): String = when (change) {
    MembershipChange.JOINED -> "$name joined"
    MembershipChange.LEFT -> "$name left"
    MembershipChange.BANNED, MembershipChange.KICKED_AND_BANNED -> "$name was banned"
    MembershipChange.UNBANNED -> "$name was unbanned"
    MembershipChange.KICKED -> "$name was removed"
    MembershipChange.INVITED -> "$name was invited"
    MembershipChange.INVITATION_ACCEPTED -> "$name accepted the invite"
    MembershipChange.INVITATION_REJECTED -> "$name declined the invite"
    MembershipChange.INVITATION_REVOKED -> "$name's invite was withdrawn"
    MembershipChange.KNOCKED -> "$name asked to join"
    MembershipChange.KNOCK_ACCEPTED -> "$name was let in"
    MembershipChange.KNOCK_RETRACTED -> "$name withdrew their request to join"
    MembershipChange.KNOCK_DENIED -> "$name's request to join was declined"
    else -> "$name's membership changed"
}

fun profileChangeText(name: String, displayName: String?, prev: String?, avatar: String?, prevAvatar: String?): String = when {
    displayName != prev && prev == null -> "$name set their name to $displayName"
    displayName != prev && displayName == null -> "${prev ?: name} removed their display name"
    displayName != prev -> "$prev changed their name to $displayName"
    avatar != prevAvatar -> "$name changed their avatar"
    else -> "$name updated their profile"
}

fun stateText(sender: String, state: OtherState): String = when (state) {
    is OtherState.RoomName -> if (state.name.isNullOrBlank()) "$sender removed the room name" else "$sender renamed the room to ${state.name}"
    is OtherState.RoomTopic -> if (state.topic.isNullOrBlank()) "$sender removed the topic" else "$sender changed the topic to: ${state.topic}"
    is OtherState.RoomAvatar -> if (state.url == null) "$sender removed the room avatar" else "$sender changed the room avatar"
    is OtherState.RoomCreate -> "$sender created the room"
    is OtherState.RoomEncryption -> "Messages here are end-to-end encrypted"
    is OtherState.RoomJoinRules -> "$sender changed who can join"
    is OtherState.RoomHistoryVisibility -> "$sender changed who can read history"
    is OtherState.RoomPowerLevels -> "$sender changed roles and permissions"
    is OtherState.RoomPinnedEvents -> "$sender changed the pinned messages"
    is OtherState.RoomCanonicalAlias -> "$sender changed the room address"
    is OtherState.RoomTombstone -> "This room has been replaced"
    is OtherState.RoomThirdPartyInvite -> "$sender invited ${state.displayName ?: "someone"}"
    is OtherState.RoomServerAcl -> "$sender changed the server access rules"
    is OtherState.RoomGuestAccess -> "$sender changed guest access"
    is OtherState.SpaceChild, is OtherState.SpaceParent -> "$sender changed the space"
    is OtherState.PolicyRuleRoom, is OtherState.PolicyRuleServer, is OtherState.PolicyRuleUser -> "$sender changed a moderation rule"
    is OtherState.Custom -> "$sender changed ${state.eventType}"
}
