package com.example.audiobookplayerbeta

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import java.io.File

class FileManager(
    private val context: android.content.Context,
    private val requestPermissionLauncher: ActivityResultLauncher<String>? = null
) {

    // Takes a path and returns a sorted list of non-directory files at that path.
    private fun loadFiles(path: String): List<FileItem> {
        return getFiles(path)
            .filter { !it.isDirectory }
            .sortedBy { it.name }
    }

    // Takes a path and returns a sorted list of directories at that path.
    fun loadFolders(path: String): List<FileItem> {
        return getFiles(path)
            .filter { it.isDirectory }
            .sortedBy { it.name }
    }

    // Takes a path and returns a combined sorted list of directories followed by files at that path.
    fun loadFoldersAndFiles(path: String): List<FileItem> {
        val files = getFiles(path)
        val folders = files.filter { it.isDirectory }.sortedBy { it.name }
        val mediaFiles = files.filter { !it.isDirectory }.sortedBy { it.name }
        return folders + mediaFiles
    }

    // Takes a path and returns the previous file in alphabetical order relative to the current file.
    fun getPreviousAudiobook(path: String): FileItem? {
        val parentPath = getParentDirectory(path)
        val files = loadFiles(parentPath)
        val currentIndex = files.indexOfFirst { it.path == path }
        return when {
            currentIndex > 0 -> files[currentIndex - 1]
            currentIndex == 0 -> files.lastOrNull()
            else -> null
        }
    }

    // Takes a path and returns the next file in alphabetical order relative to the current file.
    fun getNextAudiobook(path: String): FileItem? {
        val parentPath = getParentDirectory(path)
        val files = loadFiles(parentPath)
        val currentIndex = files.indexOfFirst { it.path == path }
        return when {
            currentIndex in 0 until files.size - 1 -> files[currentIndex + 1]
            currentIndex == files.size - 1 -> files.firstOrNull()
            else -> null
        }
    }

    // Checks if the READ_MEDIA_AUDIO permission is granted.
    fun checkPermission(): Boolean {
        val permissionStatus = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        return permissionStatus == PackageManager.PERMISSION_GRANTED
    }

    // Requests the READ_MEDIA_AUDIO permission.
    fun requestPermission() {
        requestPermissionLauncher?.launch(Manifest.permission.READ_MEDIA_AUDIO)
    }

    // Takes a file path and returns the path of its parent directory.
    fun getParentDirectory(path: String): String {
        return File(path).parent ?: ""
    }

    // Takes a path and returns a list of FileItem objects representing files and directories at that path.
    private fun getFiles(path: String): List<FileItem> {
        val file = File(path)
        return file.listFiles()?.map {
            FileItem(it.name, it.isDirectory, path = it.absolutePath)
        } ?: emptyList()
    }
}


data class FileItem(
    val name: String,
    val isDirectory: Boolean,
    val path: String
)
