package com.mezon.mobile.util

object Webhook {
    const val CHANNEL_WEBHOOK_DOCS_URL = "https://mezon.ai/docs/en/developer/webhooks/channel-webhook"
    const val CLAN_WEBHOOK_DOCS_URL = "https://mezon.ai/docs/en/developer/webhooks/clan-webhook"

    const val WEBHOOK_NAME_MAX = 64
    const val REQ_PICK_WEBHOOK_AVATAR = 4410
    const val MAX_WEBHOOK_AVATAR_BYTES = 8 * 1024 * 1024

    val PRESET_NAMES = listOf(
        "Captain hook",
        "Spidey bot",
        "Komu Knight",
    )

    fun presetAvatarUrls(baseImg: String): List<String> {
        val base = baseImg.trimEnd('/')
        return listOf(
            "$base/1787707828677382144/1791037204600983552/1787691797724532700/211_0mezon_logo_white.png",
            "$base/1787707828677382144/1791037204600983552/1787691797724532700/211_1mezon_logo_black.png",
            "$base/0/1833395573034586112/1787375123666309000/955_0mezon_logo.png",
        )
    }
}
