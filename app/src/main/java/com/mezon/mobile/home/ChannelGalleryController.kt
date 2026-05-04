package com.mezon.mobile.home

import android.util.Log
import com.mezon.mobile.BuildConfig
import com.mezon.mezon.api.ChannelAttachment
import com.mezon.mobile.core.NotificationCenter
import com.mezon.mobile.di.ApplicationScope
import com.mezon.mobile.di.IoDispatcher
import com.mezon.mobile.network.MezonApi
import com.mezon.mobile.session.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChannelGalleryCtl"
private const val PAGE_LIMIT = 50

data class ChannelGalleryMediaItem(
    val id: Long,
    val url: String,
    val filetype: String,
    val createTimeSeconds: Int,
    val uploaderId: Long,
    val messageId: Long
) {
    val isVideo: Boolean get() = filetype.lowercase(Locale.US).startsWith("video/")
}

private class ChannelGalleryState {
    val items = ArrayList<ChannelGalleryMediaItem>()
    var hasMoreBefore = true
    var initialLoading = false
    var pagingLoading = false
    var initialLoadFinished = false
}

@Singleton
class ChannelGalleryController @Inject constructor(
    private val api: MezonApi,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private val stateByChannel = HashMap<Long, ChannelGalleryState>()
    private val mutexByChannel = HashMap<Long, Mutex>()

    fun getItems(channelId: Long): List<ChannelGalleryMediaItem> =
        synchronized(stateByChannel) {
            stateByChannel[channelId]?.items?.toList() ?: emptyList()
        }

    fun hasMoreBefore(channelId: Long): Boolean =
        synchronized(stateByChannel) {
            stateByChannel[channelId]?.hasMoreBefore != false
        }

    fun isInitialLoading(channelId: Long): Boolean =
        synchronized(stateByChannel) {
            stateByChannel[channelId]?.initialLoading ?: false
        }

    fun isPagingLoading(channelId: Long): Boolean =
        synchronized(stateByChannel) {
            stateByChannel[channelId]?.pagingLoading ?: false
        }

    fun isInitialLoadFinished(channelId: Long): Boolean =
        synchronized(stateByChannel) {
            stateByChannel[channelId]?.initialLoadFinished == true
        }

    fun clearAndReload(channelId: Long, clanId: Long) {
        synchronized(stateByChannel) {
            val st = stateByChannel[channelId] ?: ChannelGalleryState()
            st.items.clear()
            st.hasMoreBefore = true
            st.initialLoadFinished = false
            st.initialLoading = false
            st.pagingLoading = false
            stateByChannel[channelId] = st
        }
        appScope.launch {
            mutexFor(channelId).withLock {
                loadImpl(channelId, clanId, isInitial = true)
            }
        }
    }

    fun fetchOlderIfNeeded(channelId: Long, clanId: Long) {
        appScope.launch {
            mutexFor(channelId).withLock {
                val st =
                    synchronized(stateByChannel) {
                        stateByChannel[channelId]
                    } ?: return@withLock

                if (!st.initialLoadFinished || st.pagingLoading || !st.hasMoreBefore || st.initialLoading) return@withLock

                if (st.items.isEmpty()) return@withLock

                loadImpl(channelId, clanId, isInitial = false)
            }
        }
    }

    private fun mutexFor(channelId: Long): Mutex =
        synchronized(mutexByChannel) {
            mutexByChannel.getOrPut(channelId) { Mutex() }
        }

    private suspend fun loadImpl(channelId: Long, clanId: Long, isInitial: Boolean) {
        val state =
            synchronized(stateByChannel) {
                stateByChannel.getOrPut(channelId) { ChannelGalleryState() }
            }

        synchronized(state) {
            when {
                isInitial -> {
                    if (state.initialLoading) return
                    state.initialLoading = true
                }

                else -> {
                    if (state.pagingLoading) return
                    state.pagingLoading = true
                }
            }
        }

        val beforeSecs: Int? =
            if (!isInitial) synchronized(state) {
                state.items.lastOrNull()?.createTimeSeconds?.takeIf { it > 0 }
            } else null

        try {
            sessionManager.withAutoRefresh { session ->
                val raw = withContext(ioDispatcher) {
                    api.listChannelAttachments(
                        apiUrl = session.apiUrl,
                        token = session.token,
                        clanId = clanId,
                        channelId = channelId,
                        limit = PAGE_LIMIT,
                        beforeTimeSeconds = beforeSecs
                    )
                }

                val batchSorted = raw.attachmentsList.mapNotNull { it.toFilteredMediaOrNull() }
                    .distinctBy { it.id }
                    .sortedWith(
                        compareByDescending<ChannelGalleryMediaItem> { it.createTimeSeconds }.thenByDescending { it.id }
                    )

                var appendedCount = 0
                synchronized(state) {
                    when {
                        isInitial -> {
                            state.items.clear()
                            state.items.addAll(batchSorted)
                            state.initialLoadFinished = true
                            state.hasMoreBefore = batchSorted.isNotEmpty()
                        }

                        else -> {
                            val idsBeforeMerge =
                                state.items.mapTo(HashSet(state.items.size + batchSorted.size)) { it.id }

                            val appended = ArrayList<ChannelGalleryMediaItem>(batchSorted.size)
                            batchSorted.forEach { cand ->
                                if (cand.id !in idsBeforeMerge && appended.none { it.id == cand.id }) {
                                    appended.add(cand)
                                }
                            }

                            appendedCount = appended.size

                            state.items.addAll(appended)
                            state.items.sortWith(
                                compareByDescending<ChannelGalleryMediaItem> { it.createTimeSeconds }
                                    .thenByDescending { it.id }
                            )

                            state.hasMoreBefore = batchSorted.any { cand -> cand.id !in idsBeforeMerge }
                        }
                    }
                }

                if (BuildConfig.DEBUG) {
                    val hm = synchronized(state) { state.hasMoreBefore }
                    Log.d(
                        TAG,
                        "loadMore ch=$channelId initial=$isInitial before=$beforeSecs " +
                            "raw=${raw.attachmentsList.size} batch=${batchSorted.size} " +
                            "appended=$appendedCount hasMoreBefore=$hm"
                    )
                }

                NotificationCenter.getGlobalInstance()
                    .postNotificationOnMainThread(NotificationCenter.channelGalleryDidLoad, channelId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "listChannelAttachments channel=$channelId", e)
            synchronized(state) {
                if (isInitial) state.initialLoadFinished = true
            }

            NotificationCenter.getGlobalInstance()
                .postNotificationOnMainThread(NotificationCenter.channelGalleryLoadError, channelId)
        } finally {
            synchronized(state) {
                when {
                    isInitial -> state.initialLoading = false
                    else -> state.pagingLoading = false
                }
            }
        }
    }

    fun cleanup() {
        synchronized(stateByChannel) {
            stateByChannel.clear()
        }
        synchronized(mutexByChannel) {
            mutexByChannel.clear()
        }
    }
}

private fun ChannelAttachment.toFilteredMediaOrNull(): ChannelGalleryMediaItem? {
    val ft = filetype.lowercase(Locale.US)
    val image = ft.startsWith("image/")
    val video = ft.startsWith("video/")
    if (!image && !video) return null

    val u = url
    if (u.isEmpty()) return null
    if (u.lowercase(Locale.US).contains("/stickers")) return null

    return ChannelGalleryMediaItem(
        id = id,
        url = u,
        filetype = filetype,
        createTimeSeconds = createTimeSeconds,
        uploaderId = uploader,
        messageId = messageId
    )
}
