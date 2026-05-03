package com.mezon.mobile.home.clans

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mezon.mezon.api.ClanDesc

@Entity(tableName = "clans", indices = [Index("clanOrder")])
data class ClanEntity(
    @PrimaryKey val clanId: Long,
    val clanName: String,
    val logo: String,
    val banner: String,
    val badgeCount: Int,
    val isCommunity: Boolean,
    val hasUnread: Boolean,
    val clanOrder: Int,
    val creatorId: Long = 0L,
    val preventAnonymous: Boolean = false,
    val welcomeChannelId: Long = 0L,
    val isOnboarding: Boolean = false,
)

fun ClanDesc.toClanEntity(): ClanEntity = ClanEntity(
    clanId = clanId,
    clanName = clanName,
    logo = logo,
    banner = banner,
    badgeCount = badgeCount,
    isCommunity = isCommunity,
    hasUnread = hasUnreadMessage,
    clanOrder = clanOrder,
    creatorId = creatorId,
    preventAnonymous = preventAnonymous,
    welcomeChannelId = welcomeChannelId,
    isOnboarding = isOnboarding,
)

fun ClanDesc.mergeOnto(existing: ClanEntity): ClanEntity =
    toClanEntity().copy(
        badgeCount = existing.badgeCount,
        hasUnread = existing.hasUnread,
        clanOrder = existing.clanOrder,
    )
