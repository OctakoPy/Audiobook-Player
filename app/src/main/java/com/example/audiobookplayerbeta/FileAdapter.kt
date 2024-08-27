package com.example.audiobookplayerbeta

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.audiobookplayerbeta.database.Audiobook
import com.example.audiobookplayerbeta.database.AudiobookDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileAdapter(
    private val lifecycleScope: LifecycleCoroutineScope,
    private val audiobookDao: AudiobookDao,
    private val onItemClick: (FileItem) -> Unit,
    private val onOptionsClick: (FileItem) -> Unit
) : ListAdapter<FileItem, RecyclerView.ViewHolder>(FileDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_FOLDER = 0
        private const val VIEW_TYPE_FILE = 1
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isDirectory) VIEW_TYPE_FOLDER else VIEW_TYPE_FILE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_FOLDER -> FolderViewHolder(inflater.inflate(R.layout.folder_item_layout, parent, false))
            VIEW_TYPE_FILE -> FileViewHolder(inflater.inflate(R.layout.media_file_item_layout, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is FolderViewHolder -> holder.bind(item)
            is FileViewHolder -> holder.bind(item)
        }
    }

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvAudiobookName)
        private val btnOptions: ImageButton = view.findViewById(R.id.hamburger)

        fun bind(item: FileItem) {
            tvName.text = item.name
            itemView.setOnClickListener { onItemClick(item) }
            btnOptions.setOnClickListener { onOptionsClick(item) }
        }
    }

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvAudiobookName)
        private val tvTimeInfo: TextView = view.findViewById(R.id.tvTimeInfo)
        private val btnOptions: ImageButton = view.findViewById(R.id.hamburger)

        fun bind(item: FileItem) {
            tvName.text = item.name
            itemView.setOnClickListener { openPlaybackActivity(item.path) }
            btnOptions.setOnClickListener { showPopupMenu(btnOptions, item) }

            lifecycleScope.launch {
                val audiobook = withContext(Dispatchers.IO) {
                    audiobookDao.getByFilePath(item.path)
                }
                withContext(Dispatchers.Main) {
                    updateTimeInfo(audiobook)
                }
            }
        }

        private fun updateTimeInfo(audiobook: Audiobook?) {
            when {
                audiobook == null -> {
                    tvTimeInfo.text = "? / ?"
                    tvTimeInfo.setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.milky_white
                        )
                    )
                }

                audiobook.isRead -> {
                    tvTimeInfo.text = "Completed!"
                    tvTimeInfo.setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.light_green_fluorescent
                        )
                    )
                }

                else -> {
                    val lastReadFormatted = formatTime(audiobook.lastReadTimestamp)
                    val durationFormatted = formatTime(audiobook.duration)
                    tvTimeInfo.text = "$lastReadFormatted / $durationFormatted"
                    tvTimeInfo.setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.milky_white
                        )
                    )
                }
            }
        }

        private fun showPopupMenu(view: View, item: FileItem) {
            val popupMenu = PopupMenu(view.context, view)

            lifecycleScope.launch {
                val audiobook = withContext(Dispatchers.IO) {
                    audiobookDao.getByFilePath(item.path)
                }

                withContext(Dispatchers.Main) {
                    inflatePopupMenu(popupMenu, audiobook)
                    setupPopupMenuListeners(popupMenu, item, audiobook)
                    popupMenu.show()
                }
            }
        }

        private fun inflatePopupMenu(popupMenu: PopupMenu, audiobook: Audiobook?) {
            val menuRes = when {
                audiobook == null -> R.menu.file_item_menu_no_audiobook
                audiobook.isRead -> R.menu.file_item_menu_read
                else -> R.menu.file_item_menu_unread
            }
            popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        }

        private fun setupPopupMenuListeners(popupMenu: PopupMenu, item: FileItem, audiobook: Audiobook?) {
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_open -> {
                        openPlaybackActivity(item.path)
                        true
                    }
                    R.id.action_mark_as_read -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            audiobookDao.markBookRead(item.path)
                        }
                        true
                    }
                    R.id.action_mark_as_unread -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            audiobookDao.markBookUnread(item.path)
                        }
                        true
                    }
                    R.id.action_delete -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            audiobookDao.deleteByFilePath(item.path)
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        private fun openPlaybackActivity(filePath: String) {
            val context = itemView.context
            val intent = Intent(context, PlaybackActivity::class.java)
            intent.putExtra("FILE_PATH", filePath)
            context.startActivity(intent)
        }
    }


    private fun formatTime(timeInMillis: Long): String {
        val totalSeconds = timeInMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

}

class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
    override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
        oldItem.path == newItem.path

    override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
        oldItem == newItem
}