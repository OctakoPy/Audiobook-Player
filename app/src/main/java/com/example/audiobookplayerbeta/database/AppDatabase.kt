package com.example.audiobookplayerbeta.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(entities = [Audiobook::class, Bookmark::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audiobookDao(): AudiobookDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "audiobook_database"
                )
                    .fallbackToDestructiveMigration(false) // Use destructive migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
