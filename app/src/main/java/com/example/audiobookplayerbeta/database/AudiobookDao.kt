package com.example.audiobookplayerbeta.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AudiobookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audiobook: Audiobook)

    @Update
    suspend fun update(audiobook: Audiobook)

    @Query("UPDATE audiobooks SET lastReadTimestamp = :timestamp WHERE filePath = :filePath")
    suspend fun updateLastReadTimestamp(filePath: String, timestamp: Long)

    @Query("UPDATE audiobooks SET playbackSpeed = :playbackSpeed WHERE filePath = :filePath")
    suspend fun updatePlaybackSpeed(filePath: String, playbackSpeed: Float)

    @Query("UPDATE audiobooks SET isRead = TRUE WHERE filePath = :filePath")
    suspend fun markBookRead(filePath: String)

    @Query("UPDATE audiobooks SET isRead = False WHERE filePath = :filePath")
    suspend fun markBookUnread(filePath: String)

    @Query("DELETE FROM audiobooks WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)

    @Query("SELECT * FROM audiobooks WHERE filePath = :filePath")
    suspend fun getByFilePath(filePath: String): Audiobook?

    @Query("SELECT duration FROM audiobooks WHERE filePath = :filePath")
    suspend fun getDuration(filePath: String): Long?

    @Query("SELECT isRead FROM audiobooks WHERE filePath = :filePath")
    suspend fun checkIsRead(filePath: String): Long?

    @Query("SELECT lastReadTimestamp FROM audiobooks WHERE filePath = :filePath")
    suspend fun getLastReadTimestamp(filePath: String): Long?

    @Query("SELECT playbackSpeed FROM audiobooks WHERE filePath = :filePath")
    suspend fun getPlaybackSpeed(filePath: String): Float?

    @Query("SELECT title FROM audiobooks WHERE filePath = :filePath")
    suspend fun getTitle(filePath: String): String?

    @Query("SELECT pitch FROM audiobooks WHERE filePath = :filePath")
    suspend fun getPlaybackPitch(filePath: String): Float?

    @Query("UPDATE audiobooks SET pitch = :pitch WHERE filePath = :filePath")
    suspend fun updatePlaybackPitch(filePath: String, pitch: Float)
}
