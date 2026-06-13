package com.mezon.mobile.home.messages

import android.content.Context
import com.mezon.mobile.home.profile.UserController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class DmPinResult {
    data object Success : DmPinResult()
    data object MaxReached : DmPinResult()
}

@Singleton
class DmPinStorage @Inject constructor(
    @ApplicationContext context: Context,
    private val userController: UserController,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPinnedIds(): List<Long> {
        val userId = userController.userId
        if (userId == 0L) return emptyList()
        val raw = prefs.getString(storageKey(userId), null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.toLongOrNull() }.filter { it != 0L }
    }

    fun isPinned(channelId: Long): Boolean = channelId != 0L && getPinnedIds().contains(channelId)

    fun pin(channelId: Long): DmPinResult {
        if (channelId == 0L) return DmPinResult.Success
        val userId = userController.userId
        if (userId == 0L) return DmPinResult.Success
        val current = getPinnedIds().toMutableList()
        if (current.contains(channelId)) return DmPinResult.Success
        if (current.size >= MAX_PINNED) return DmPinResult.MaxReached
        current.add(channelId)
        save(userId, current)
        return DmPinResult.Success
    }

    fun unpin(channelId: Long) {
        removeFromPinIfPresent(channelId)
    }

    fun removeFromPinIfPresent(channelId: Long) {
        if (channelId == 0L) return
        val userId = userController.userId
        if (userId == 0L) return
        val current = getPinnedIds().toMutableList()
        if (!current.remove(channelId)) return
        save(userId, current)
    }

    private fun save(userId: Long, ids: List<Long>) {
        prefs.edit()
            .putString(storageKey(userId), ids.joinToString(","))
            .apply()
    }

    private fun storageKey(userId: Long): String = "pinned_dm_$userId"

    companion object {
        const val MAX_PINNED = 10
        private const val PREFS_NAME = "dm_pin_storage"
    }
}
