package com.example.audiobookplayerbeta

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Start PlaybackService
        val serviceIntent = Intent(this, PlaybackService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}