package com.example.audiobookplayerbeta.database

import androidx.room.*

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    @Update
    suspend fun update(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks WHERE audiobookId = :audiobookId")
    suspend fun getBookmarksForAudiobook(audiobookId: String): List<Bookmark>

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Int)
}
