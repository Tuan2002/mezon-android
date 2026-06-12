package com.mezon.mobile.home.clans

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowEmptyCategoryStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(clanId: Long): Boolean {
        if (clanId == 0L) return false
        return prefs.getBoolean(key(clanId), false)
    }

    fun setEnabled(clanId: Long, enabled: Boolean) {
        if (clanId == 0L) return
        prefs.edit().putBoolean(key(clanId), enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "show_empty_category"
        private fun key(clanId: Long) = "show_$clanId"
    }
}
