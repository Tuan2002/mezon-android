package com.mezon.mobile.home.sharing

import android.net.Uri

sealed class SharingPayload {
    data class FromDevice(
        val uris: List<Uri>,
        val text: String?,
        val mimeType: String?
    ) : SharingPayload()

    data class ForwardFromChat(
        val sourceChannelId: Long,
        val sourceClanId: Long,
        val sourceChannelType: Int
    ) : SharingPayload()
}
