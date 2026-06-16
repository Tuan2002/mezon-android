package com.mezon.mobile.home.clans

import android.util.Log
import com.mezon.mezon.api.ChannelCanvasDetailResponse
import com.mezon.mezon.api.ChannelCanvasItem
import com.mezon.mobile.core.NotificationCenter
import com.mezon.mobile.di.ApplicationScope
import com.mezon.mobile.di.IoDispatcher
import com.mezon.mobile.network.ApiCacheTracker
import com.mezon.mobile.network.CHANNEL_TYPE_DM
import com.mezon.mobile.network.CHANNEL_TYPE_GROUP
import com.mezon.mobile.network.MezonApi
import com.mezon.mobile.network.apiCacheKey
import com.mezon.mobile.session.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

data class ChannelCanvasData(
    val id: Long,
    val title: String,
    val content: String,
    val isDefault: Boolean,
    val creatorId: Long,
    val createTimeSeconds: Int,
    val updateTimeSeconds: Int
)

private const val TAG = "ChannelCanvasController"
private const val CANVAS_LIST_CACHE_TTL_MS = 5 * 60 * 1_000L
private const val CANVAS_DETAIL_CACHE_TTL_MS = 2 * 60 * 1_000L

@Singleton
class ChannelCanvasController @Inject constructor(
    private val api: MezonApi,
    private val sessionManager: SessionManager,
    private val notificationCenter: NotificationCenter,
    private val apiCacheTracker: ApiCacheTracker,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private val canvasesByChannel = HashMap<Long, ArrayList<ChannelCanvasData>>()
    private val canvasesRevisionByChannel = HashMap<Long, Int>()
    private val detailByKey = HashMap<String, ChannelCanvasData>()
    private val loadFailed = Collections.synchronizedSet(HashSet<Long>())
    private val loadingChannels = Collections.synchronizedSet(HashSet<Long>())
    private val loadingDetails = Collections.synchronizedSet(HashSet<String>())

    fun getCanvases(channelId: Long): List<ChannelCanvasData> {
        synchronized(this) {
            return canvasesByChannel[channelId] ?: emptyList()
        }
    }

    fun getCanvasesRevision(channelId: Long): Int {
        synchronized(this) {
            return canvasesRevisionByChannel[channelId] ?: 0
        }
    }

    fun getCanvasDetail(channelId: Long, canvasId: Long): ChannelCanvasData? {
        synchronized(this) {
            return detailByKey[detailKey(channelId, canvasId)]
        }
    }

    fun isFetching(channelId: Long): Boolean = synchronized(loadingChannels) { channelId in loadingChannels }

    fun isFetchingDetail(channelId: Long, canvasId: Long): Boolean {
        synchronized(loadingDetails) { return detailKey(channelId, canvasId) in loadingDetails }
    }

    fun loadChannelCanvases(
        channelId: Long,
        clanId: Long,
        channelType: Int,
        forceRefresh: Boolean = false
    ) {
        val apiClanId = resolveApiClanId(clanId, channelType)
        val cacheKey = apiCacheKey("channelCanvases", channelId, apiClanId)
        if (!forceRefresh &&
            apiCacheTracker.shouldCall(cacheKey, ttlMs = CANVAS_LIST_CACHE_TTL_MS) == ApiCacheTracker.ShouldCall.SKIP &&
            synchronized(this) { canvasesByChannel.containsKey(channelId) }
        ) {
            notificationCenter.postNotificationOnMainThread(NotificationCenter.channelCanvasesDidLoad, channelId)
            return
        }
        synchronized(loadingChannels) {
            if (!loadingChannels.add(channelId)) return
        }
        appScope.launch {
            try {
                sessionManager.withAutoRefresh { session ->
                    val response = withContext(ioDispatcher) {
                        api.getChannelCanvasList(session.apiUrl, session.token, apiClanId, channelId)
                    }
                    val list = response.channelCanvasesList.map { it.toChannelCanvasData() }
                    synchronized(this@ChannelCanvasController) {
                        canvasesByChannel[channelId] = ArrayList(list)
                        bumpCanvasesRevision(channelId)
                    }
                    synchronized(loadFailed) { loadFailed.remove(channelId) }
                    apiCacheTracker.markCalled(cacheKey, ttlMs = CANVAS_LIST_CACHE_TTL_MS)
                    synchronized(loadingChannels) { loadingChannels.remove(channelId) }
                    notificationCenter.postNotificationOnMainThread(NotificationCenter.channelCanvasesDidLoad, channelId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "getChannelCanvasList failed channel=$channelId", e)
                synchronized(loadFailed) { loadFailed.add(channelId) }
                apiCacheTracker.invalidate(cacheKey)
                synchronized(loadingChannels) { loadingChannels.remove(channelId) }
                notificationCenter.postNotificationOnMainThread(NotificationCenter.channelCanvasesLoadError, channelId)
            }
        }
    }

    fun loadCanvasDetail(
        channelId: Long,
        clanId: Long,
        channelType: Int,
        canvasId: Long,
        forceRefresh: Boolean = false
    ) {
        val apiClanId = resolveApiClanId(clanId, channelType)
        val key = detailKey(channelId, canvasId)
        val cacheKey = apiCacheKey("channelCanvasDetail", channelId, canvasId, apiClanId)
        if (!forceRefresh &&
            apiCacheTracker.shouldCall(cacheKey, ttlMs = CANVAS_DETAIL_CACHE_TTL_MS) == ApiCacheTracker.ShouldCall.SKIP &&
            synchronized(this) { detailByKey.containsKey(key) }
        ) {
            notificationCenter.postNotificationOnMainThread(
                NotificationCenter.channelCanvasDetailDidLoad, channelId, canvasId
            )
            return
        }
        synchronized(loadingDetails) {
            if (!loadingDetails.add(key)) return
        }
        appScope.launch {
            try {
                sessionManager.withAutoRefresh { session ->
                    val response = withContext(ioDispatcher) {
                        api.getChannelCanvasDetail(session.apiUrl, session.token, canvasId, apiClanId, channelId)
                    }
                    val data = response.toChannelCanvasData(channelId)
                    synchronized(this@ChannelCanvasController) {
                        detailByKey[key] = data
                        val list = canvasesByChannel[channelId]
                        if (list != null) {
                            val idx = list.indexOfFirst { it.id == canvasId }
                            if (idx >= 0) {
                                val existing = list[idx]
                                list[idx] = existing.copy(
                                    title = data.title,
                                    isDefault = data.isDefault,
                                    creatorId = data.creatorId,
                                    updateTimeSeconds = data.updateTimeSeconds,
                                )
                                bumpCanvasesRevision(channelId)
                            } else {
                                list.add(data.copy(content = ""))
                                bumpCanvasesRevision(channelId)
                            }
                        }
                    }
                    apiCacheTracker.markCalled(cacheKey, ttlMs = CANVAS_DETAIL_CACHE_TTL_MS)
                    notificationCenter.postNotificationOnMainThread(
                        NotificationCenter.channelCanvasDetailDidLoad, channelId, canvasId
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "getChannelCanvasDetail failed channel=$channelId canvas=$canvasId", e)
                apiCacheTracker.invalidate(cacheKey)
                notificationCenter.postNotificationOnMainThread(
                    NotificationCenter.channelCanvasDetailLoadError, channelId, canvasId
                )
            } finally {
                synchronized(loadingDetails) { loadingDetails.remove(key) }
            }
        }
    }

    private fun resolveApiClanId(clanId: Long, channelType: Int): Long {
        return if (channelType == CHANNEL_TYPE_DM || channelType == CHANNEL_TYPE_GROUP) 0L else clanId
    }

    private fun detailKey(channelId: Long, canvasId: Long): String = "$channelId:$canvasId"

    private fun bumpCanvasesRevision(channelId: Long) {
        canvasesRevisionByChannel[channelId] = (canvasesRevisionByChannel[channelId] ?: 0) + 1
    }
}

private fun ChannelCanvasItem.toChannelCanvasData(): ChannelCanvasData = ChannelCanvasData(
    id = id,
    title = title,
    content = "",
    isDefault = isDefault,
    creatorId = creatorId,
    createTimeSeconds = createTimeSeconds,
    updateTimeSeconds = updateTimeSeconds
)

private fun ChannelCanvasDetailResponse.toChannelCanvasData(channelId: Long): ChannelCanvasData = ChannelCanvasData(
    id = id,
    title = title,
    content = content,
    isDefault = isDefault,
    creatorId = creatorId,
    createTimeSeconds = 0,
    updateTimeSeconds = 0
)
