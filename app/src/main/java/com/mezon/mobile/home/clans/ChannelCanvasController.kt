package com.mezon.mobile.home.clans

import android.util.Log
import com.mezon.mezon.api.ChannelCanvasDetailResponse
import com.mezon.mezon.api.ChannelCanvasItem
import com.mezon.mezon.api.ChannelCanvasListResponse
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

object ChannelCanvasStatus {
    const val CREATED = 1
    const val UPDATE = 2
}

fun canDeleteChannelCanvas(canvas: ChannelCanvasData, currentUserId: Long): Boolean {
    return canvas.creatorId == 0L || canvas.creatorId == currentUserId
}

fun canEditChannelCanvas(canvas: ChannelCanvasData, currentUserId: Long): Boolean {
    return canDeleteChannelCanvas(canvas, currentUserId)
}

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
    private val detailByKey = HashMap<String, ChannelCanvasData>()
    private val loadFailed = Collections.synchronizedSet(HashSet<Long>())
    private val loadingChannels = Collections.synchronizedSet(HashSet<Long>())
    private val loadingDetails = Collections.synchronizedSet(HashSet<String>())

    fun getCanvases(channelId: Long): List<ChannelCanvasData> {
        synchronized(this) {
            return canvasesByChannel[channelId]?.toList() ?: emptyList()
        }
    }

    fun getCanvasDetail(channelId: Long, canvasId: Long): ChannelCanvasData? {
        synchronized(this) {
            return detailByKey[detailKey(channelId, canvasId)]
        }
    }

    fun hadLoadError(channelId: Long): Boolean = synchronized(loadFailed) { channelId in loadFailed }

    fun isFetching(channelId: Long): Boolean = synchronized(loadingChannels) { channelId in loadingChannels }

    fun isFetchingDetail(channelId: Long, canvasId: Long): Boolean {
        synchronized(loadingDetails) { return detailKey(channelId, canvasId) in loadingDetails }
    }

    fun resolveApiClanId(clanId: Long, channelType: Int): Long {
        return if (channelType == CHANNEL_TYPE_DM || channelType == CHANNEL_TYPE_GROUP) 0L else clanId
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
                    }
                    synchronized(loadFailed) { loadFailed.remove(channelId) }
                    apiCacheTracker.markCalled(cacheKey, ttlMs = CANVAS_LIST_CACHE_TTL_MS)
                    notificationCenter.postNotificationOnMainThread(NotificationCenter.channelCanvasesDidLoad, channelId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "getChannelCanvasList failed channel=$channelId", e)
                synchronized(loadFailed) { loadFailed.add(channelId) }
                apiCacheTracker.invalidate(cacheKey)
                notificationCenter.postNotificationOnMainThread(NotificationCenter.channelCanvasesLoadError, channelId)
            } finally {
                synchronized(loadingChannels) { loadingChannels.remove(channelId) }
            }
        }
    }

    fun loadCanvasDetail(
        channelId: Long,
        clanId: Long,
        channelType: Int,
        canvasId: Long,
        forceRefresh: Boolean = false,
        onComplete: ((ChannelCanvasData?) -> Unit)? = null
    ) {
        val apiClanId = resolveApiClanId(clanId, channelType)
        val key = detailKey(channelId, canvasId)
        val cacheKey = apiCacheKey("channelCanvasDetail", channelId, canvasId, apiClanId)
        if (!forceRefresh &&
            apiCacheTracker.shouldCall(cacheKey, ttlMs = CANVAS_DETAIL_CACHE_TTL_MS) == ApiCacheTracker.ShouldCall.SKIP &&
            synchronized(this) { detailByKey.containsKey(key) }
        ) {
            val cached = synchronized(this) { detailByKey[key] }
            notificationCenter.postNotificationOnMainThread(
                NotificationCenter.channelCanvasDetailDidLoad, channelId, canvasId
            )
            onComplete?.invoke(cached)
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
                            if (idx >= 0) list[idx] = data else list.add(data)
                        }
                    }
                    apiCacheTracker.markCalled(cacheKey, ttlMs = CANVAS_DETAIL_CACHE_TTL_MS)
                    notificationCenter.postNotificationOnMainThread(
                        NotificationCenter.channelCanvasDetailDidLoad, channelId, canvasId
                    )
                    onComplete?.invoke(data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "getChannelCanvasDetail failed channel=$channelId canvas=$canvasId", e)
                apiCacheTracker.invalidate(cacheKey)
                notificationCenter.postNotificationOnMainThread(
                    NotificationCenter.channelCanvasDetailLoadError, channelId, canvasId
                )
                onComplete?.invoke(null)
            } finally {
                synchronized(loadingDetails) { loadingDetails.remove(key) }
            }
        }
    }

    fun deleteCanvas(
        channelId: Long,
        clanId: Long,
        channelType: Int,
        canvasId: Long,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val apiClanId = resolveApiClanId(clanId, channelType)
        appScope.launch {
            var success = false
            try {
                sessionManager.withAutoRefresh { session ->
                    withContext(ioDispatcher) {
                        api.deleteChannelCanvas(session.apiUrl, session.token, apiClanId, channelId, canvasId)
                    }
                    synchronized(this@ChannelCanvasController) {
                        canvasesByChannel[channelId]?.removeAll { it.id == canvasId }
                        detailByKey.remove(detailKey(channelId, canvasId))
                    }
                    apiCacheTracker.invalidate(apiCacheKey("channelCanvases", channelId, apiClanId))
                    apiCacheTracker.invalidate(apiCacheKey("channelCanvasDetail", channelId, canvasId, apiClanId))
                    success = true
                    notificationCenter.postNotificationOnMainThread(
                        NotificationCenter.channelCanvasDeleted, channelId, canvasId
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteChannelCanvas failed channel=$channelId canvas=$canvasId", e)
            }
            onComplete?.invoke(success)
        }
    }

    fun createCanvas(
        channelId: Long,
        clanId: Long,
        channelType: Int,
        title: String,
        content: String,
        creatorId: Long,
        isDefault: Boolean = false,
        onComplete: ((Long?) -> Unit)? = null
    ) {
        saveCanvas(
            channelId = channelId,
            clanId = clanId,
            channelType = channelType,
            canvasId = null,
            title = title,
            content = content,
            isDefault = isDefault,
            status = ChannelCanvasStatus.CREATED,
            creatorId = creatorId,
            onComplete = onComplete
        )
    }

    fun updateCanvas(
        channelId: Long,
        clanId: Long,
        channelType: Int,
        canvasId: Long,
        title: String,
        content: String,
        isDefault: Boolean = false,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        saveCanvas(
            channelId = channelId,
            clanId = clanId,
            channelType = channelType,
            canvasId = canvasId,
            title = title,
            content = content,
            isDefault = isDefault,
            status = ChannelCanvasStatus.UPDATE,
            onComplete = { savedId -> onComplete?.invoke(savedId != null) }
        )
    }

    fun saveCanvas(
        channelId: Long,
        clanId: Long,
        channelType: Int,
        canvasId: Long?,
        title: String,
        content: String,
        isDefault: Boolean = false,
        status: Int,
        creatorId: Long = 0L,
        onComplete: ((Long?) -> Unit)? = null
    ) {
        val apiClanId = resolveApiClanId(clanId, channelType)
        appScope.launch {
            var savedId: Long? = null
            try {
                sessionManager.withAutoRefresh { session ->
                    val response = withContext(ioDispatcher) {
                        api.editChannelCanvas(
                            session.apiUrl,
                            session.token,
                            id = canvasId ?: 0L,
                            channelId = channelId,
                            clanId = apiClanId,
                            title = title,
                            content = content,
                            isDefault = isDefault,
                            status = status
                        )
                    }
                    savedId = response.id
                    val resolvedCreatorId = synchronized(this@ChannelCanvasController) {
                        val existing = detailByKey[detailKey(channelId, response.id)]?.creatorId ?: 0L
                        if (existing != 0L) existing else creatorId
                    }
                    val data = ChannelCanvasData(
                        id = response.id,
                        title = title,
                        content = content,
                        isDefault = isDefault,
                        creatorId = resolvedCreatorId,
                        createTimeSeconds = 0,
                        updateTimeSeconds = 0
                    )
                    synchronized(this@ChannelCanvasController) {
                        detailByKey[detailKey(channelId, response.id)] = data
                        val list = canvasesByChannel.getOrPut(channelId) { ArrayList() }
                        val idx = list.indexOfFirst { it.id == response.id }
                        if (idx >= 0) list[idx] = data else list.add(0, data)
                    }
                    apiCacheTracker.invalidate(apiCacheKey("channelCanvases", channelId, apiClanId))
                    apiCacheTracker.invalidate(apiCacheKey("channelCanvasDetail", channelId, response.id, apiClanId))
                    notificationCenter.postNotificationOnMainThread(
                        NotificationCenter.channelCanvasSaved, channelId, response.id
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "editChannelCanvas failed channel=$channelId", e)
            }
            onComplete?.invoke(savedId)
        }
    }

    fun cleanup() {
        synchronized(this) {
            canvasesByChannel.clear()
            detailByKey.clear()
        }
        synchronized(loadFailed) { loadFailed.clear() }
        synchronized(loadingChannels) { loadingChannels.clear() }
        synchronized(loadingDetails) { loadingDetails.clear() }
    }

    private fun detailKey(channelId: Long, canvasId: Long): String = "$channelId:$canvasId"
}

private fun ChannelCanvasItem.toChannelCanvasData(): ChannelCanvasData = ChannelCanvasData(
    id = id,
    title = title,
    content = content,
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
