package com.example.audiobookplayerbeta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.audiobookplayerbeta.database.AppDatabase
import com.example.audiobookplayerbeta.database.AudiobookDao

class MenuActivity : AppCompatActivity() {
    private lateinit var fileManager: FileManager
    private lateinit var adapter: FileAdapter
    private lateinit var rvFiles: RecyclerView
    private lateinit var tvDirectoryPath: TextView
    private var currentPath: String = ""
    private lateinit var audiobookDao: AudiobookDao
    private lateinit var sharedPrefs: SharedPreferencesManager

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            loadDirectory(currentPath)
        } else {
            Toast.makeText(this, "Permission denied. Cannot access audio files.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        // Initialize FileManager
        fileManager = FileManager(this)

        // Initialize AudiobookDao
        val database = AppDatabase.getDatabase(applicationContext)
        audiobookDao = database.audiobookDao()

        // Initialize SharedPreferences
        sharedPrefs = SharedPreferencesManager(applicationContext)

        // Initialize views
        rvFiles = findViewById(R.id.rvFiles)
        tvDirectoryPath = findViewById(R.id.tvDirectoryPath)
        val btnPreviousFolder: ImageButton = findViewById(R.id.btnPreviousFolder)
        val btnSettings: ImageButton = findViewById(R.id.settings)
        val btnCurrentlyPlaying: ImageButton = findViewById(R.id.currentlyPlaying)

        // Set up RecyclerView
        adapter = FileAdapter(
            lifecycleScope,
            audiobookDao,
            onItemClick = { clickedItem ->
                if (clickedItem.isDirectory) {
                    loadDirectory(clickedItem.path)
                } else {
                    // Handle file click (e.g., play audio)
                    // val intent = Intent(this, PlaybackActivity::class.java).apply {
                    //     putExtra("FILE_PATH", clickedItem.path)
                    // }
                    // startActivity(intent)
                }
            },
            onOptionsClick = { _ ->
                // TODO: Implement options menu functionality
            }
        )
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = adapter

        // Get initial path
        currentPath = intent.getStringExtra("FILE_PATH") ?: Environment.getExternalStorageDirectory().path
        checkPermissionAndLoadDirectory()

        // Set up button click listeners
        btnPreviousFolder.setOnClickListener {
            val parentPath = fileManager.getParentDirectory(currentPath)
            if (parentPath.isNotEmpty()) {
                loadDirectory(parentPath)
            }
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }


        btnCurrentlyPlaying.setOnClickListener {
            openCurrentlyPlaying()
        }
    }

    private fun checkPermissionAndLoadDirectory() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                loadDirectory(currentPath)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO) -> {
                Toast.makeText(this, "Audio permission is required to access audiobooks", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }
    }

    private fun loadDirectory(path: String) {
        currentPath = path
        tvDirectoryPath.text = currentPath
        val items = fileManager.loadFoldersAndFiles(currentPath)
        adapter.submitList(items)
    }

    private fun openCurrentlyPlaying() {
        if (sharedPrefs.lastRead != null) {
            val intent = Intent(this, PlaybackActivity::class.java).apply {
                putExtra("FILE_PATH", sharedPrefs.lastRead)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "No audiobook is currently playing.", Toast.LENGTH_SHORT).show()
        }
    }
}
