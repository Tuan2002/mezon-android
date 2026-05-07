package com.mezon.mobile.core

import android.content.Context
import android.content.SharedPreferences
import com.mezon.mobile.home.chat.poll.PollVotePersistence
import com.mezon.mobile.ui.theme.ThemeMode

object StartupCache {

    @Volatile
    var suppressHomeListApiForIncomingCallWake: Boolean = false

    private const val PREFS_NAME = "mezon_startup_cache"
    private const val KEY_HAS_SESSION = "has_session"
    private const val KEY_THEME = "theme"
    private const val KEY_LOCALE = "locale"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NEEDS_USERNAME_SETUP = "needs_username_setup"

    private lateinit var prefs: SharedPreferences

    var hasSession: Boolean
        get() = prefs.getBoolean(KEY_HAS_SESSION, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SESSION, value).apply()

    var themeMode: ThemeMode
        get() = when (prefs.getString(KEY_THEME, null)) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            "abyss" -> ThemeMode.ABYSS
            "system" -> ThemeMode.SYSTEM
            else -> ThemeMode.DARK
        }
        set(value) = prefs.edit().putString(KEY_THEME, when (value) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.ABYSS -> "abyss"
            ThemeMode.SYSTEM -> "system"
        }).apply()

    var locale: String
        get() = prefs.getString(KEY_LOCALE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LOCALE, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var needsUsernameSetup: Boolean
        get() = prefs.getBoolean(KEY_NEEDS_USERNAME_SETUP, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NEEDS_USERNAME_SETUP, value).commit()
        }

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        PollVotePersistence.attach(context)
    }

    fun seed(hasSession: Boolean, themeMode: String, locale: String) {
        prefs.edit()
            .putBoolean(KEY_HAS_SESSION, hasSession)
            .putString(KEY_THEME, themeMode)
            .putString(KEY_LOCALE, locale)
            .apply {
                if (!hasSession) {
                    putBoolean(KEY_NEEDS_USERNAME_SETUP, false)
                }
            }
            .commit()
    }
}
