package com.vknxworks.elementctrl.matrix

import org.matrix.rustcomponents.sdk.MembershipChange
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.OtherState
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.TimelineItemContent
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

/** Display name from a profile, falling back to the localpart of the Matrix ID. */
fun ProfileDetails.nameOr(userId: String): String =
    (this as? ProfileDetails.Ready)?.displayName?.takeIf { it.isNotBlank() } ?: userId.localpart()

fun ProfileDetails.avatarOrNull(): String? = (this as? ProfileDetails.Ready)?.avatarUrl

fun String.localpart(): String = removePrefix("@").substringBefore(':')

/** One-line text for a message, used in the room list, replies and notifications. */
fun TimelineItemContent.previewText(): String = when (this) {
    is TimelineItemContent.MsgLike -> when (val kind = content.kind) {
        is MsgLikeKind.Message -> kind.content.msgType.previewText(kind.content.body)
        is MsgLikeKind.Sticker -> Strings.str(R.string.sticker, kind.body)
        is MsgLikeKind.Poll -> "📊 ${kind.question}"
        is MsgLikeKind.Redacted -> Strings.str(R.string.message_deleted)
        is MsgLikeKind.UnableToDecrypt -> Strings.str(R.string.waiting_for_this_message)
        is MsgLikeKind.LiveLocation -> Strings.str(R.string.live_location)
        is MsgLikeKind.Other -> Strings.str(R.string.unsupported_event)
    }
    is TimelineItemContent.RoomMembership -> membershipText(userDisplayName ?: userId.localpart(), change)
    is TimelineItemContent.ProfileChange -> profileChangeText(Strings.str(R.string.invite_someone), displayName, prevDisplayName, avatarUrl, prevAvatarUrl)
    is TimelineItemContent.State -> stateText(Strings.str(R.string.invite_someone), content)
    is TimelineItemContent.CallInvite -> Strings.str(R.string.call_started_2)
    is TimelineItemContent.RtcNotification -> Strings.str(R.string.call)
    is TimelineItemContent.FailedToParseMessageLike -> Strings.str(R.string.unsupported_message)
    is TimelineItemContent.FailedToParseState -> Strings.str(R.string.unsupported_room_event)
}

fun MessageType.previewText(body: String): String = when (this) {
    is MessageType.Image -> "🖼 " + (content.caption ?: Strings.str(R.string.image))
    is MessageType.Video -> "🎬 " + (content.caption ?: Strings.str(R.string.video))
    is MessageType.Audio -> if (content.voice != null) Strings.str(R.string.voice_message) else "🎵 " + (content.caption ?: content.filename)
    is MessageType.File -> "📎 " + (content.caption ?: content.filename)
    is MessageType.Location -> Strings.str(R.string.location)
    is MessageType.Gallery -> "🖼 " + content.body.ifBlank { Strings.str(R.string.gallery) }
    is MessageType.Emote -> "* " + content.body
    is MessageType.Notice -> content.body
    is MessageType.Text -> content.body
    is MessageType.Other -> body
}

fun membershipText(name: String, change: MembershipChange?): String = when (change) {
    MembershipChange.JOINED -> Strings.str(R.string.joined, name)
    MembershipChange.LEFT -> Strings.str(R.string.left, name)
    MembershipChange.BANNED, MembershipChange.KICKED_AND_BANNED -> Strings.str(R.string.was_banned, name)
    MembershipChange.UNBANNED -> Strings.str(R.string.was_unbanned, name)
    MembershipChange.KICKED -> Strings.str(R.string.was_removed, name)
    MembershipChange.INVITED -> Strings.str(R.string.was_invited, name)
    MembershipChange.INVITATION_ACCEPTED -> Strings.str(R.string.accepted_the_invite, name)
    MembershipChange.INVITATION_REJECTED -> Strings.str(R.string.declined_the_invite, name)
    MembershipChange.INVITATION_REVOKED -> Strings.str(R.string.s_invite_was_withdrawn, name)
    MembershipChange.KNOCKED -> Strings.str(R.string.asked_to_join, name)
    MembershipChange.KNOCK_ACCEPTED -> Strings.str(R.string.was_let_in, name)
    MembershipChange.KNOCK_RETRACTED -> Strings.str(R.string.withdrew_their_request_to_join, name)
    MembershipChange.KNOCK_DENIED -> Strings.str(R.string.s_request_to_join_was_declined, name)
    else -> Strings.str(R.string.s_membership_changed, name)
}

fun profileChangeText(name: String, displayName: String?, prev: String?, avatar: String?, prevAvatar: String?): String = when {
    displayName != prev && prev == null -> Strings.str(R.string.set_their_name_to, name, displayName)
    displayName != prev && displayName == null -> Strings.str(R.string.removed_their_display_name, prev ?: name)
    displayName != prev -> Strings.str(R.string.changed_their_name_to, prev, displayName)
    avatar != prevAvatar -> Strings.str(R.string.changed_their_avatar, name)
    else -> Strings.str(R.string.updated_their_profile, name)
}

fun stateText(sender: String, state: OtherState): String = when (state) {
    is OtherState.RoomName -> if (state.name.isNullOrBlank()) Strings.str(R.string.removed_the_room_name, sender) else Strings.str(R.string.renamed_the_room_to, sender, state.name)
    is OtherState.RoomTopic -> if (state.topic.isNullOrBlank()) Strings.str(R.string.removed_the_topic, sender) else Strings.str(R.string.changed_the_topic_to, sender, state.topic)
    is OtherState.RoomAvatar -> if (state.url == null) Strings.str(R.string.removed_the_room_avatar, sender) else Strings.str(R.string.changed_the_room_avatar, sender)
    is OtherState.RoomCreate -> Strings.str(R.string.created_the_room, sender)
    is OtherState.RoomEncryption -> Strings.str(R.string.messages_here_are_end_to_end)
    is OtherState.RoomJoinRules -> Strings.str(R.string.changed_who_can_join, sender)
    is OtherState.RoomHistoryVisibility -> Strings.str(R.string.changed_who_can_read_history, sender)
    is OtherState.RoomPowerLevels -> Strings.str(R.string.changed_roles_and_permissions, sender)
    is OtherState.RoomPinnedEvents -> Strings.str(R.string.changed_the_pinned_messages, sender)
    is OtherState.RoomCanonicalAlias -> Strings.str(R.string.changed_the_room_address, sender)
    is OtherState.RoomTombstone -> Strings.str(R.string.this_room_has_been_replaced)
    is OtherState.RoomThirdPartyInvite -> Strings.str(R.string.invited, sender, state.displayName ?: "someone")
    is OtherState.RoomServerAcl -> Strings.str(R.string.changed_the_server_access_rules, sender)
    is OtherState.RoomGuestAccess -> Strings.str(R.string.changed_guest_access, sender)
    is OtherState.SpaceChild, is OtherState.SpaceParent -> Strings.str(R.string.changed_the_space, sender)
    is OtherState.PolicyRuleRoom, is OtherState.PolicyRuleServer, is OtherState.PolicyRuleUser -> Strings.str(R.string.changed_a_moderation_rule, sender)
    is OtherState.Custom -> Strings.str(R.string.changed, sender, state.eventType)
}
