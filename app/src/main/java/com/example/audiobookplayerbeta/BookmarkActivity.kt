package com.example.audiobookplayerbeta

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.audiobookplayerbeta.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarkActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var bookmarkAdapter: BookmarkAdapter
    private lateinit var btnBackToMainActivity: ImageButton
    private var currentAudiobookPath: String? = null

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val bookmarkDao by lazy { database.bookmarkDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bookmark_menu)

        recyclerView = findViewById(R.id.rvBookmarks)
        btnBackToMainActivity = findViewById(R.id.btnBackToMainActivity)

        setupRecyclerView()
        setupBackButton()

        // Get the current audiobook path from intent
        currentAudiobookPath = intent.getStringExtra(EXTRA_AUDIOBOOK_PATH)

        loadBookmarks()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        bookmarkAdapter = BookmarkAdapter(
            emptyList(),
            onItemClick = { bookmark -> onBookmarkClicked(bookmark) },
            onDeleteClick = { bookmark -> onDeleteBookmark(bookmark) }
        )
        recyclerView.adapter = bookmarkAdapter
    }

    private fun setupBackButton() {
        btnBackToMainActivity.setOnClickListener {
            finish()
        }
    }

    private fun loadBookmarks() {
        CoroutineScope(Dispatchers.IO).launch {
            val bookmarks = currentAudiobookPath?.let { path ->
                bookmarkDao.getBookmarksForAudiobook(path)
            } ?: emptyList()

            // Sort bookmarks by timestamp in ascending order
            val sortedBookmarks = bookmarks.sortedBy { it.timestamp }

            val bookmarkItems = sortedBookmarks.map { bookmark ->
                BookmarkItem(
                    id = bookmark.id,
                    audiobookId = bookmark.audiobookId,
                    timestamp = bookmark.timestamp,
                    description = bookmark.description
                )
            }

            withContext(Dispatchers.Main) {
                bookmarkAdapter.updateBookmarks(bookmarkItems)
            }
        }
    }


    private fun onBookmarkClicked(bookmark: BookmarkItem) {
        // Return the selected bookmark position to PlaybackActivity
        val resultIntent = Intent().apply {
            putExtra(EXTRA_SELECTED_BOOKMARK_POSITION, bookmark.timestamp)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun onDeleteBookmark(bookmark: BookmarkItem) {
        CoroutineScope(Dispatchers.IO).launch {
            bookmarkDao.deleteBookmark(bookmark.id)
            loadBookmarks()
        }
    }

    companion object {
        const val EXTRA_AUDIOBOOK_PATH = "extra_audiobook_path"
        const val EXTRA_SELECTED_BOOKMARK_POSITION = "extra_selected_bookmark_position"
    }
}