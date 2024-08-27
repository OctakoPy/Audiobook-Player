package com.example.audiobookplayerbeta

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.audiobookplayerbeta.database.AppDatabase
import com.example.audiobookplayerbeta.database.Bookmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookmarkManager(private val context: Context) {
    private val database: AppDatabase = AppDatabase.getDatabase(context)
    private val bookmarkDao = database.bookmarkDao()

    fun createBookmark(currentFilePath: String, currentPosition: Long) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Add Bookmark")

        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        builder.setPositiveButton("OK") { dialog, _ ->
            val userDescription = input.text.toString().trim()
            val description = userDescription.ifEmpty {
                "Bookmark at ${formatTime(currentPosition)}"
            }

            val bookmark = Bookmark(
                audiobookId = currentFilePath,
                timestamp = currentPosition,
                description = description
            )

            CoroutineScope(Dispatchers.IO).launch {
                bookmarkDao.insert(bookmark)
            }

            Toast.makeText(context, "Bookmark created", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60)) % 24
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}