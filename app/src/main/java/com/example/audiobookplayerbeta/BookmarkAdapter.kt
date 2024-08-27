package com.example.audiobookplayerbeta

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BookmarkAdapter(
    private var bookmarks: List<BookmarkItem>,
    private val onItemClick: (BookmarkItem) -> Unit,
    private val onDeleteClick: (BookmarkItem) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder>() {

    class BookmarkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.bookmarkTitle)
        val timestampTextView: TextView = view.findViewById(R.id.bookmarkTimestamp)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteBookmark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.bookmark_menu_layout, parent, false)
        return BookmarkViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        val bookmark = bookmarks[position]
        holder.titleTextView.text = bookmark.description ?: "Bookmark ${position + 1}"
        holder.timestampTextView.text = bookmark.getFormattedTimestamp()

        holder.itemView.setOnClickListener { onItemClick(bookmark) }
        holder.deleteButton.setOnClickListener { onDeleteClick(bookmark) }
    }

    override fun getItemCount() = bookmarks.size

    fun updateBookmarks(newBookmarks: List<BookmarkItem>) {
        bookmarks = newBookmarks
        notifyDataSetChanged()
    }
}