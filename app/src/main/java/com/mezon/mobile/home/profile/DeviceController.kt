package com.mezon.mobile.home.profile

import com.mezon.mobile.core.NotificationCenter
import com.mezon.mobile.di.ApplicationScope
import com.mezon.mobile.di.IoDispatcher
import com.mezon.mobile.network.ApiCacheTracker
import com.mezon.mobile.network.MezonApi
import com.mezon.mobile.network.apiCacheKey
import com.mezon.mobile.session.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceController @Inject constructor(
    private val api: MezonApi,
    private val sessionManager: SessionManager,
    private val notificationCenter: NotificationCenter,
    private val cacheTracker: ApiCacheTracker,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val cachedDevices = ArrayList<Device>()
    private var hasCachedDevices = false

    suspend fun fetchDevices(forceRefresh: Boolean = false): Result<List<Device>> = withContext(ioDispatcher) {
        val cacheKey = apiCacheKey("listLogedDevice")
        if (!forceRefresh) {
            val cached = synchronized(this@DeviceController) {
                if (hasCachedDevices) ArrayList(cachedDevices) else null
            }
            if (cached != null && cacheTracker.shouldCall(cacheKey) == ApiCacheTracker.ShouldCall.SKIP) {
                return@withContext Result.success(cached)
            }
        }
        try {
            val response = sessionManager.withAutoRefresh { session ->
                api.listLogedDevice(session.apiUrl, session.token)
            }
            val devices = response.devicesList.map { deviceInfo ->
                Device(
                    deviceId = deviceInfo.deviceId.ifEmpty { "" },
                    deviceName = if (deviceInfo.deviceName.isNotEmpty()) deviceInfo.deviceName else null,
                    ip = if (deviceInfo.ip.isNotEmpty()) deviceInfo.ip else null,
                    lastActiveSeconds = deviceInfo.lastActiveSeconds.toLong(),
                    loginAtSeconds = deviceInfo.loginAtSeconds.toLong(),
                    platform = if (deviceInfo.platform.isNotEmpty()) deviceInfo.platform else null,
                    status = deviceInfo.status,
                    isCurrentDevice = deviceInfo.isCurrent,
                    location = if (deviceInfo.location.isNotEmpty()) deviceInfo.location else null
                )
            }
            synchronized(this@DeviceController) {
                cachedDevices.clear()
                cachedDevices.addAll(devices)
                hasCachedDevices = true
            }
            cacheTracker.markCalled(cacheKey)
            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
