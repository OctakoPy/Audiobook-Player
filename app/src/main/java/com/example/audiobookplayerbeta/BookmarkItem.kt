package com.example.audiobookplayerbeta

import android.annotation.SuppressLint

data class BookmarkItem(
    val id: Int,
    val audiobookId: String,
    val timestamp: Long,
    val description: String?
) {
    @SuppressLint("DefaultLocale")
    fun getFormattedTimestamp(): String {
        val seconds = (timestamp / 1000) % 60
        val minutes = (timestamp / (1000 * 60)) % 60
        val hours = (timestamp / (1000 * 60 * 60)) % 24
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}