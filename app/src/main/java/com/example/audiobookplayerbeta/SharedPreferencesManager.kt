package com.example.audiobookplayerbeta

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "AudioBookPlayerPrefs"
        private const val REWIND_VALUE_KEY = "RewindValue"
        private const val LAST_READ_KEY = "LastRead"
        private const val AUTOPLAY_NEXT_KEY = "AutoplayNext"
        private const val AUTOPLAY_FROM_TIMESTAMP_KEY = "AutoplayFromTimestamp"

        private const val DEFAULT_REWIND_VALUE = 10
        private const val DEFAULT_AUTOPLAY_NEXT = true
        private const val DEFAULT_AUTOPLAY_FROM_TIMESTAMP = false
    }

    var rewindValue: Int
        get() = prefs.getInt(REWIND_VALUE_KEY, DEFAULT_REWIND_VALUE)
        set(value) = prefs.edit().putInt(REWIND_VALUE_KEY, value).apply()

    var lastRead: String?
        get() = prefs.getString(LAST_READ_KEY, null)
        set(value) = prefs.edit().putString(LAST_READ_KEY, value).apply()

    var autoplayNext: Boolean
        get() = prefs.getBoolean(AUTOPLAY_NEXT_KEY, DEFAULT_AUTOPLAY_NEXT)
        set(value) = prefs.edit().putBoolean(AUTOPLAY_NEXT_KEY, value).apply()

    var autoplayFromTimestamp: Boolean
        get() = prefs.getBoolean(AUTOPLAY_FROM_TIMESTAMP_KEY, DEFAULT_AUTOPLAY_FROM_TIMESTAMP)
        set(value) = prefs.edit().putBoolean(AUTOPLAY_FROM_TIMESTAMP_KEY, value).apply()
}