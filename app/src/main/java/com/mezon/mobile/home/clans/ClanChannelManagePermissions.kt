package com.mezon.mobile.home.clans

object ClanChannelManagePermissions {

    fun canOfferCreateChannelInCategory(clanId: Long): Boolean = clanId != 0L
}
