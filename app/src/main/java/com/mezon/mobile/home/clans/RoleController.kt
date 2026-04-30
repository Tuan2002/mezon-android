package com.mezon.mobile.home.clans

import android.graphics.Color
import android.util.Log
import com.mezon.mobile.core.NotificationCenter
import com.mezon.mobile.di.ApplicationScope
import com.mezon.mobile.di.IoDispatcher
import com.mezon.mobile.di.MainDispatcher
import com.mezon.mobile.network.MezonApi
import com.mezon.mobile.session.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RoleController"

@Singleton
class RoleController @Inject constructor(
    private val api: MezonApi,
    private val sessionManager: SessionManager,
    private val notificationCenter: NotificationCenter,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher
) {
    private val _rolesByClan = MutableStateFlow<Map<Long, List<ClanRole>>>(emptyMap())
    val rolesByClan: StateFlow<Map<Long, List<ClanRole>>> = _rolesByClan.asStateFlow()

    private val loadingClans = ConcurrentHashMap<Long, Boolean>()
    private val clanLoadLocks = ConcurrentHashMap<Long, Any>()
    private val clanRoleLoadWaiters = ConcurrentHashMap<Long, CopyOnWriteArrayList<Runnable>>()

    fun getRoles(clanId: Long): List<ClanRole> =
        _rolesByClan.value[clanId] ?: emptyList()

    fun forgetClanRoles(clanId: Long) {
        if (clanId == 0L) return
        val m = _rolesByClan.value.toMutableMap()
        m.remove(clanId)
        _rolesByClan.value = m
        loadingClans.remove(clanId)
        clanLoadLocks.remove(clanId)
        clanRoleLoadWaiters.remove(clanId)
    }

    fun cleanup() {
        _rolesByClan.value = emptyMap()
        loadingClans.clear()
        clanRoleLoadWaiters.clear()
        clanLoadLocks.clear()
    }

    fun loadRolesForClan(clanId: Long, force: Boolean = false) {
        if (clanId <= 0) return
        val lock = clanLoadLocks.computeIfAbsent(clanId) { Any() }
        synchronized(lock) {
            if (!force && loadingClans[clanId] == true) return
            if (!force && !_rolesByClan.value[clanId].isNullOrEmpty()) return
            loadingClans[clanId] = true
        }
        appScope.launch(ioDispatcher) {
            try {
                loadRolesForClanSync(clanId)
            } finally {
                finalizeAfterRoleLoad(clanId, null)
            }
        }
    }

    fun loadRolesForClanThen(clanId: Long, force: Boolean = true, onComplete: Runnable) {
        if (clanId <= 0) {
            appScope.launch(mainDispatcher) { onComplete.run() }
            return
        }
        val lock = clanLoadLocks.computeIfAbsent(clanId) { Any() }
        synchronized(lock) {
            if (!force && !_rolesByClan.value[clanId].isNullOrEmpty()) {
                appScope.launch(mainDispatcher) { onComplete.run() }
                return
            }
            if (!force && loadingClans[clanId] == true) {
                appScope.launch(mainDispatcher) { onComplete.run() }
                return
            }
            if (loadingClans[clanId] == true) {
                clanRoleLoadWaiters.computeIfAbsent(clanId) { CopyOnWriteArrayList() }.add(onComplete)
                return
            }
            loadingClans[clanId] = true
        }
        appScope.launch(ioDispatcher) {
            try {
                loadRolesForClanSync(clanId)
            } finally {
                finalizeAfterRoleLoad(clanId, onComplete)
            }
        }
    }

    private suspend fun finalizeAfterRoleLoad(clanId: Long, primary: Runnable?) {
        loadingClans[clanId] = false
        withContext(mainDispatcher) {
            primary?.run()
            clanRoleLoadWaiters.remove(clanId)?.forEach { it.run() }
        }
    }

    private suspend fun loadRolesForClanSync(clanId: Long) {
        try {
            val response = sessionManager.withAutoRefresh { session ->
                api.listRoles(session.apiUrl, session.token, clanId)
            }
            val everyoneSlug = "everyone-$clanId"
            val roles = response.roles.rolesList
                .asSequence()
                .filter { it.slug != everyoneSlug }
                .map { proto ->
                    val permSlugs = if (proto.hasPermissionList()) {
                        proto.permissionList.permissionsList.map { it.slug }.filter { s -> s.isNotEmpty() }
                    } else {
                        emptyList()
                    }
                    ClanRole(
                        roleId = proto.id,
                        clanId = clanId,
                        title = proto.title,
                        color = parseHexColor(proto.color),
                        iconUrl = proto.roleIcon,
                        slug = proto.slug,
                        permissionSlugs = permSlugs
                    )
                }
                .toList()
            val updated = _rolesByClan.value.toMutableMap().apply {
                put(clanId, roles)
            }
            _rolesByClan.value = updated
            Log.d(TAG, "Loaded ${roles.size} roles for clan $clanId")
            notificationCenter.postNotificationOnMainThread(NotificationCenter.clanRolesDidLoad, clanId)
        } catch (e: Exception) {
            Log.e(TAG, "loadRolesForClan failed for clan $clanId", e)
        }
    }

    private fun parseHexColor(raw: String): Int {
        if (raw.isBlank()) return 0
        val hex = if (raw.startsWith("#")) raw else "#$raw"
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            0
        }
    }
}
