package com.example.audiobookplayerbeta

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {

    private lateinit var sharedPreferencesManager: SharedPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferencesManager = SharedPreferencesManager(this)

        val intent = if (sharedPreferencesManager.lastRead == null) {
            Intent(this, MenuActivity::class.java)
        } else {
            Intent(this, PlaybackActivity::class.java).apply {
                putExtra("FILE_PATH", sharedPreferencesManager.lastRead)
            }
        }

        startActivity(intent)
        finish()
    }
}