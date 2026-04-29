package com.mezon.mobile.home.chat

internal object ForwardNavigationStash {

    var pendingMessages: ArrayList<MessageEntity>? = null

    fun takeMessages(): ArrayList<MessageEntity>? {
        val p = pendingMessages
        pendingMessages = null
        return p
    }
}
