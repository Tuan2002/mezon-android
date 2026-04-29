package com.mezon.mobile.home

import android.util.Log
import com.mezon.mezon.api.ChannelAttachment
import com.mezon.mobile.core.NotificationCenter
import com.mezon.mobile.di.ApplicationScope
import com.mezon.mobile.di.IoDispatcher
import com.mezon.mobile.home.chat.channelinfo.ChannelDocumentItem
import com.mezon.mobile.network.ApiCacheTracker
import com.mezon.mobile.network.MezonApi
import com.mezon.mobile.network.apiCacheKey
import com.mezon.mobile.session.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChannelFilesController"
private const val FILES_CACHE_TTL_MS = 60 * 60 * 1_000L

@Singleton
class ChannelFilesController @Inject constructor(
    private val api: MezonApi,
    private val sessionManager: SessionManager,
    private val notificationCenter: NotificationCenter,
    private val apiCacheTracker: ApiCacheTracker,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private val docsByChannel = HashMap<Long, ArrayList<ChannelDocumentItem>>()
    private val loadFailed = Collections.synchronizedSet(HashSet<Long>())
    private val loadingChannels = Collections.synchronizedSet(HashSet<Long>())

    fun getDocuments(channelId: Long): List<ChannelDocumentItem> {
        synchronized(this) {
            return docsByChannel[channelId]?.toList() ?: emptyList()
        }
    }

    fun hadLoadError(channelId: Long): Boolean = synchronized(loadFailed) { channelId in loadFailed }

    fun isFetching(channelId: Long): Boolean = synchronized(loadingChannels) { channelId in loadingChannels }

    fun loadChannelFiles(channelId: Long, clanId: Long, forceRefresh: Boolean = false) {
        val cacheKey = apiCacheKey("channelFiles", channelId)
        if (!forceRefresh &&
            apiCacheTracker.shouldCall(cacheKey, ttlMs = FILES_CACHE_TTL_MS) == ApiCacheTracker.ShouldCall.SKIP &&
            synchronized(this@ChannelFilesController) { docsByChannel.containsKey(channelId) }
        ) {
            notificationCenter.postNotificationOnMainThread(NotificationCenter.channelFilesDidLoad, channelId)
            return
        }
        synchronized(loadingChannels) {
            if (!loadingChannels.add(channelId)) return
        }
        appScope.launch {
            try {
                sessionManager.withAutoRefresh { session ->
                    val raw = withContext(ioDispatcher) {
                        api.listChannelAttachments(session.apiUrl, session.token, clanId, channelId, limit = 100)
                    }
                    val list = raw.attachmentsList.mapNotNull { it.toFilteredDocumentOrNull() }
                        .distinctBy { it.stableId }
                    synchronized(this@ChannelFilesController) {
                        docsByChannel[channelId] = ArrayList(list)
                    }
                    synchronized(loadFailed) { loadFailed.remove(channelId) }
                    apiCacheTracker.markCalled(cacheKey, ttlMs = FILES_CACHE_TTL_MS)
                    notificationCenter.postNotificationOnMainThread(NotificationCenter.channelFilesDidLoad, channelId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "listChannelAttachments failed channel=$channelId", e)
                synchronized(loadFailed) { loadFailed.add(channelId) }
                apiCacheTracker.invalidate(cacheKey)
                notificationCenter.postNotificationOnMainThread(NotificationCenter.channelFilesLoadError, channelId)
            } finally {
                synchronized(loadingChannels) { loadingChannels.remove(channelId) }
            }
        }
    }
}

private fun ChannelAttachment.toFilteredDocumentOrNull(): ChannelDocumentItem? {
    val ft = filetype.lowercase(Locale.US)
    if (ft.startsWith("image")) return null
    if (ft.startsWith("video")) return null
    if (ft == "sticker") return null
    val urlStr = url
    if (urlStr.isEmpty()) return null
    val msgId = messageId
    return ChannelDocumentItem(
        stableId = "${msgId}_$urlStr",
        filename = filename.ifBlank { "File" },
        filetype = filetype.ifBlank { "File" },
        url = urlStr,
        uploader = uploader,
        createTimeSeconds = createTimeSeconds,
        messageId = msgId
    )
}
