package com.mezon.mobile.home.sharing

import com.mezon.mobile.home.chat.ForwardDestination
import com.mezon.mobile.home.clans.ClanChannelEntity
import com.mezon.mobile.home.messages.DirectMessage
import com.mezon.mobile.network.CHANNEL_TYPE_DM
import com.mezon.mobile.network.CHANNEL_TYPE_GROUP
import com.mezon.mobile.network.CHANNEL_TYPE_THREAD

data class SharingTarget(
    val channelId: Long,
    val channelLabel: String,
    val avatarUrl: String,
    val clanId: Long,
    val clanName: String,
    val clanLogo: String,
    val channelType: Int,
    val isPrivate: Boolean,
    val parentId: Long,
    val parentChannelLabel: String,
    val lastActivityTs: Long
) {
    val isDm: Boolean get() = channelType == CHANNEL_TYPE_DM
    val isGroup: Boolean get() = channelType == CHANNEL_TYPE_GROUP
    val isThread: Boolean get() = channelType == CHANNEL_TYPE_THREAD || parentId != 0L
    val isClanChannel: Boolean get() = !isDm && !isGroup && clanId != 0L
}

fun SharingTarget.toForwardDestination(): ForwardDestination = ForwardDestination(
    channelId = channelId,
    clanId = clanId,
    channelType = channelType,
    displayLabel = channelLabel,
    subtitle = if (isClanChannel && clanName.isNotEmpty()) clanName else "",
    isChannelPrivate = isPrivate
)

fun DirectMessage.toSharingTarget(): SharingTarget = SharingTarget(
    channelId = channelId,
    channelLabel = displayName.ifBlank { label },
    avatarUrl = avatarUrl,
    clanId = 0L,
    clanName = "",
    clanLogo = "",
    channelType = type,
    isPrivate = type == CHANNEL_TYPE_DM || type == CHANNEL_TYPE_GROUP,
    parentId = 0L,
    parentChannelLabel = "",
    lastActivityTs = maxOf(lastSeenMessageTs, lastSentMessageTs)
)

fun ClanChannelEntity.toSharingTarget(
    clanName: String,
    clanLogo: String,
    parentChannelLabel: String = ""
): SharingTarget {
    return SharingTarget(
        channelId = channelId,
        channelLabel = channelLabel,
        avatarUrl = "",
        clanId = clanId,
        clanName = clanName,
        clanLogo = clanLogo,
        channelType = type,
        isPrivate = isPrivate,
        parentId = parentId,
        parentChannelLabel = parentChannelLabel,
        lastActivityTs = maxOf(lastSeenMessageTs, lastSentMessageTs)
    )
}
