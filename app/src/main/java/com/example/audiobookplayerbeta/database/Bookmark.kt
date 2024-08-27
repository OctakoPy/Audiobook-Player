package com.example.audiobookplayerbeta.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val audiobookId: String,
    val timestamp: Long,
    val description: String? = null
)
