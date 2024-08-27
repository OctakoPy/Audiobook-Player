package com.example.audiobookplayerbeta.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audiobooks")
data class Audiobook(
    @PrimaryKey val filePath: String,
    val lastReadTimestamp: Long,
    val duration: Long,
    val title: String,
    val isRead: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val pitch: Float = 1.0f
)
