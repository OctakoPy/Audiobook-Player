package com.example.audiobookplayerbeta

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.audiobookplayerbeta.database.AppDatabase
import com.example.audiobookplayerbeta.database.Audiobook
import com.example.audiobookplayerbeta.database.AudiobookDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class PlaybackService : LifecycleService() {

    interface PlaybackCallback {
        fun onNewAudiobookStarted(filePath: String)
    }

    private var playbackCallback: PlaybackCallback? = null

    fun setPlaybackCallback(callback: PlaybackActivity) {
        playbackCallback = callback
    }

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var sharedPrefs: SharedPreferencesManager
    private lateinit var audiobookDao: AudiobookDao
    private lateinit var fileManager: FileManager
    private var notificationManager: NotificationManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var currentFilePath: String? = null
    private var currentPosition: Int = 0
    private var amplificationLevel: Float = 1.0f
    private var timerJob: Job? = null
    private var timerDuration: Long = 0
    private var timerRemaining: Long = 0

    private var playbackState = PlaybackState.STOPPED
    private var lastActionTimestamp = 0L
    private val actionDebounceTime = 300L // milliseconds

    private enum class PlaybackState {
        PLAYING, STOPPED, COMPLETING
    }

    private val binder = PlaybackBinder()

    private lateinit var audioManager: AudioManager
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "Bluetooth disconnected or audio becoming noisy")
                stopAudio()
            }
        }
    }
    private var isReceiverRegistered = false

    inner class PlaybackBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    fun getCurrentPosition(filePath: String): Int? {
        return if (filePath == currentFilePath) {
            mediaPlayer.currentPosition
        } else {
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer()
        fileManager = FileManager(applicationContext)
        initializeDatabase()
        sharedPrefs = SharedPreferencesManager(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Register BroadcastReceiver for audio becoming noisy (including Bluetooth disconnection)
        if (!isReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(bluetoothReceiver, filter)
            isReceiverRegistered = true
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, PlaybackActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val title = currentFilePath?.let {
            runBlocking { getAudiobookTitle(it) }
        } ?: "No audiobook playing"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_equalizer)
            .setContentTitle(title)
            .setContentText("Audiobook Player")
            .setSubText("Audiobook Player")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSortKey("A") // This ensures the notification stays at the top
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))

        // Add rewind action
        val rewindIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_REWIND
        }
        val rewindPendingIntent = PendingIntent.getService(this, 0, rewindIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(NotificationCompat.Action(R.drawable.ic_fast_rewind_small, "Previous", rewindPendingIntent))

        // Add play/pause action
        val playPauseIntent = Intent(this, PlaybackService::class.java).apply {
            action = if (mediaPlayer.isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(this, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val playPauseIcon = if (mediaPlayer.isPlaying) R.drawable.ic_pause_small else R.drawable.ic_play_small
        val playPauseText = if (mediaPlayer.isPlaying) "Pause" else "Play"
        builder.addAction(NotificationCompat.Action(playPauseIcon, playPauseText, playPausePendingIntent))

        // Add fast forward action
        val fastForwardIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_FAST_FORWARD
        }
        val fastForwardPendingIntent = PendingIntent.getService(this, 2, fastForwardIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(NotificationCompat.Action(R.drawable.ic_fast_forward_small, "Next", fastForwardPendingIntent))

        return builder.build()
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> {
                handlePlayAction()
                updateNotification() // Update notification after play action
            }
            ACTION_PAUSE -> {
                handlePauseAction()
                updateNotification() // Update notification after pause action
            }
            ACTION_REWIND -> {
                handleRewindAction()
                updateNotification() // Update notification after rewind action
            }
            ACTION_FAST_FORWARD -> {
                handleFastForwardAction()
                updateNotification() // Update notification after fast forward action
            }
        }
        return START_STICKY
    }

    private fun handlePlayAction() {
        currentFilePath?.let { filePath ->
            if (mediaPlayer.isPlaying) {
                Log.d("PlaybackService", "handlePlayAction: Audio is already playing, pausing")
                stopAudio()
            } else {
                Log.d("PlaybackService", "handlePlayAction: Starting audio playback for file path: $filePath")
                playAudio(filePath)
            }
            updateNotification()
        } ?: Log.w("PlaybackService", "handlePlayAction: currentFilePath is null")
    }

    private fun handlePauseAction() {
        if (mediaPlayer.isPlaying) {
            Log.e("PlaybackService", "handlePauseAction: Pausing audio playback")
            stopAudio()
            updateNotification()
        } else {
            Log.w("PlaybackService", "handlePauseAction: MediaPlayer is not playing")
        }
    }

    private fun handleRewindAction() {
        currentFilePath?.let { filePath ->
            Log.e("PlaybackService", "handleRewindAction: Rewinding audio for file path: $filePath")
            rewind(filePath)
            updateNotification()
        } ?: Log.w("PlaybackService", "handleRewindAction: currentFilePath is null")
    }

    private fun handleFastForwardAction() {
        currentFilePath?.let { filePath ->
            Log.e("PlaybackService", "handleFastForwardAction: Fast-forwarding audio for file path: $filePath")
            fastForward(filePath)
            updateNotification()
        } ?: Log.w("PlaybackService", "handleFastForwardAction: currentFilePath is null")
    }


    fun setTimer(minutes: Int) {
        timerDuration = minutes * 60 * 1000L // Convert to milliseconds
        timerRemaining = timerDuration
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (timerRemaining > 0) {
                delay(1000) // Update every second
                timerRemaining -= 1000
            }
            if (timerRemaining <= 0) {
                stopAudio()
            }
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerDuration = 0
        timerRemaining = 0
    }

    fun getTimerRemaining(): Long {
        return timerRemaining
    }


    suspend fun getPlaybackPitch(filePath: String): Float {
        return audiobookDao.getPlaybackPitch(filePath) ?: 1.0f
    }

    fun setPitch(filePath: String, pitch: Float) {
        serviceScope.launch {
            audiobookDao.updatePlaybackPitch(filePath, pitch)
            if (filePath == currentFilePath) {
                updatePitch(pitch)
            }
        }
    }

    private fun updatePitch(pitch: Float) {
        if (pitch in 0.5f..2.0f) { // Pitch range, 1.0 is normal
            serviceScope.launch {
                withContext(Dispatchers.Main) {
                    mediaPlayer.playbackParams = mediaPlayer.playbackParams.setPitch(pitch)
                    Log.d(TAG, "Pitch updated to: $pitch")
                }
            }
        } else {
            Log.e(TAG, "Pitch value out of range")
        }
    }

    fun getAmplificationLevel(filePath: String): Float {
        Log.d(TAG, "Getting amplification level for file: $filePath")
        Log.d(TAG, "Current amplification level: $amplificationLevel")
        return amplificationLevel
    }

    fun setAmplificationLevel(filePath: String, level: Float) {
        Log.d(TAG, "Setting amplification level for file: $filePath to $level")
        serviceScope.launch {
            val clampedLevel = level.coerceIn(1.0f, 2.0f) // Ensure level is between 1.0 and 2.0
            amplificationLevel = clampedLevel
            Log.d(TAG, "Clamped amplification level: $clampedLevel")
            updateAmplification(clampedLevel)
        }
    }

    private fun updateAmplification(level: Float) {
        Log.d(TAG, "Updating amplification to level: $level")
        serviceScope.launch {
            withContext(Dispatchers.Main) {
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val adjustedVolume = (currentVolume * level).toInt().coerceAtMost(maxVolume)

                Log.d(TAG, "Current volume: $currentVolume")
                Log.d(TAG, "Max volume: $maxVolume")
                Log.d(TAG, "Calculated adjusted volume: $adjustedVolume")

                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, adjustedVolume, 0)
                Log.d(TAG, "Amplification level updated to: $level with volume set to: $adjustedVolume")
            }
        }
    }


    private fun updateNotification() {
        notificationManager?.notify(NOTIFICATION_ID, createNotification())
    }


    private fun initializeDatabase() {
        val database = AppDatabase.getDatabase(applicationContext)
        audiobookDao = database.audiobookDao()
    }

    suspend fun getOrCreateAudiobook(filePath: String): Audiobook {
        var audiobook = audiobookDao.getByFilePath(filePath)
        if (audiobook == null) {
            val duration = getDuration(filePath)
            val title = getTitle(filePath)
            audiobook = Audiobook(filePath = filePath, lastReadTimestamp = 0, duration = duration, title = title)
            audiobookDao.insert(audiobook)
        }
        return audiobook
    }

    fun fastForward(filePath: String) {
        if (filePath == currentFilePath) {
            serviceScope.launch {
                val rewindValueSeconds = sharedPrefs.rewindValue
                val rewindValueMillis = rewindValueSeconds * 1000
                val newPosition = mediaPlayer.currentPosition + rewindValueMillis
                mediaPlayer.seekTo(newPosition)
                Log.d("PlaybackService", "Fast forwarded by $rewindValueSeconds seconds. New position: ${newPosition}ms")
            }
        }
    }

    fun rewind(filePath: String) {
        if (filePath == currentFilePath) {
            serviceScope.launch {
                val rewindValueSeconds = sharedPrefs.rewindValue
                val rewindValueMillis = rewindValueSeconds * 1000
                val newPosition = mediaPlayer.currentPosition - rewindValueMillis
                mediaPlayer.seekTo(newPosition.coerceAtLeast(0)) // Ensure position does not go below 0
                Log.d("PlaybackService", "Rewinded by $rewindValueSeconds seconds. New position: ${newPosition.coerceAtLeast(0)}ms")
            }
        }
    }



    private suspend fun getDuration(filePath: String): Long {
        return withContext(Dispatchers.IO) {
            val tempPlayer = MediaPlayer()
            return@withContext try {
                tempPlayer.setDataSource(filePath)
                tempPlayer.prepare()
                tempPlayer.duration.toLong()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting total duration", e)
                0
            } finally {
                tempPlayer.release()
            }
        }
    }

    private suspend fun getTitle(filePath: String): String {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                val file = File(filePath)
                if (file.exists()) {
                    file.name
                } else {
                    Log.e(TAG, "File does not exist")
                    ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file name", e)
                ""
            }
        }
    }

    fun isAudiobookPlaying(filePath: String): Boolean {
        return filePath == currentFilePath && mediaPlayer.isPlaying
    }

    fun seekTo(position: Int) {
        serviceScope.launch {
            if (position in 0..mediaPlayer.duration) {
                mediaPlayer.seekTo(position)
            }
        }
    }

    private fun handleAudiobookCompletion() {
        serviceScope.launch {
            val autoplayNext = sharedPrefs.autoplayNext
            Log.d(TAG, "Audiobook completed. Autoplay next: $autoplayNext")

            currentFilePath?.let {
                updateLastReadTimestamp(it, true)
                audiobookDao.markBookRead(it)
                Log.d(TAG, "Marked audiobook as read: $it")
            }

            if (autoplayNext) {
                val nextFilePath = getNextAudiobook()
                if (nextFilePath != null) {
                    Log.d(TAG, "Starting next audiobook: $nextFilePath")
                    playNextAudiobook(nextFilePath)
                } else {
                    Log.d(TAG, "No next audiobook found")
                    playbackState = PlaybackState.STOPPED
                }
            } else {
                Log.d(TAG, "Autoplay is off, stopping playback")
                playbackState = PlaybackState.STOPPED
            }

            updateNotification()
        }
    }

    private fun playNextAudiobook(filePath: String) {
        serviceScope.launch {
            try {
                // Get or create the audiobook
                val audiobook = getOrCreateAudiobook(filePath)

                // Update the current file path
                currentFilePath = filePath

                // Prepare and start playback
                mediaPlayer.reset()
                mediaPlayer.setDataSource(filePath)
                val playbackSpeed = getPlaybackSpeed(filePath)
                val pitch = getPlaybackPitch(filePath)

                mediaPlayer.setOnPreparedListener { mp ->
                    mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed).setPitch(pitch)
                    mp.seekTo(0) // Start from the beginning
                    mp.start()
                    playbackState = PlaybackState.PLAYING
                    updateNotification()
                    Log.d(TAG, "Started playing next audiobook: $filePath from the beginning")
                }

                mediaPlayer.setOnCompletionListener {
                    Log.d(TAG, "Playback completed: $filePath")
                    if (playbackState == PlaybackState.PLAYING) {
                        playbackState = PlaybackState.COMPLETING
                        handleAudiobookCompletion()
                    }
                }

                mediaPlayer.prepareAsync()
                sharedPrefs.lastRead = filePath

                // Notify callback if set
                playbackCallback?.onNewAudiobookStarted(filePath)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing next audiobook", e)
                playbackState = PlaybackState.STOPPED
            }
        }
    }

    fun playAudio(filePath: String, startFromBeginning: Boolean = false) {
        serviceScope.launch {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastActionTimestamp < actionDebounceTime) {
                Log.d(TAG, "Ignoring rapid play action")
                return@launch
            }
            lastActionTimestamp = currentTime

            if (playbackState == PlaybackState.COMPLETING) {
                Log.d(TAG, "Ignoring play action during completion")
                return@launch
            }

            playbackState = PlaybackState.PLAYING

            if (filePath != currentFilePath) {
                currentFilePath?.let { updateLastReadTimestamp(it) }
            }

            currentFilePath = filePath
            val audiobook = getOrCreateAudiobook(filePath)
            Log.d(TAG, "Preparing to play: $filePath")

            mediaPlayer.reset()
            try {
                mediaPlayer.setDataSource(filePath)
                val playbackSpeed = withContext(Dispatchers.IO) { getPlaybackSpeed(filePath) }
                val pitch = withContext(Dispatchers.IO) { getPlaybackPitch(filePath) }

                mediaPlayer.setOnPreparedListener { mp ->
                    mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed).setPitch(pitch)
                    currentPosition = if (startFromBeginning) 0 else audiobook.lastReadTimestamp.toInt()
                    mp.seekTo(currentPosition)
                    mp.start()
                    playbackState = PlaybackState.PLAYING
                    updateNotification()
                    Log.d(TAG, "Started playing: $filePath from position: $currentPosition")
                }

                mediaPlayer.setOnCompletionListener {
                    Log.d(TAG, "Playback completed: $filePath")
                    if (playbackState == PlaybackState.PLAYING) {
                        playbackState = PlaybackState.COMPLETING
                        handleAudiobookCompletion()
                    }
                }

                mediaPlayer.prepareAsync()
                sharedPrefs.lastRead = filePath
            } catch (e: Exception) {
                Log.e(TAG, "Error setting data source", e)
                playbackState = PlaybackState.STOPPED
            }
            updateNotification()
        }
    }

    fun skipToNextAudiobook() {
        serviceScope.launch {
            val nextFilePath = getNextAudiobook()
            if (nextFilePath != null) {
                playAudio(nextFilePath, startFromBeginning = true)
                playbackCallback?.onNewAudiobookStarted(nextFilePath)
            } else {
                Log.d(TAG, "No next audiobook available")
            }
        }
    }

    fun skipToPreviousAudiobook() {
        serviceScope.launch {
            val previousFilePath = getPreviousAudiobook()
            if (previousFilePath != null) {
                playAudio(previousFilePath, startFromBeginning = true)
                playbackCallback?.onNewAudiobookStarted(previousFilePath)
            } else {
                Log.d(TAG, "No previous audiobook available")
            }
        }
    }


    suspend fun getNextAudiobook(): String? {
        return withContext(Dispatchers.IO) {
            currentFilePath?.let { fileManager.getNextAudiobook(it)?.path }
        }
    }

    suspend fun getPreviousAudiobook(): String? {
        return withContext(Dispatchers.IO) {
            currentFilePath?.let { fileManager.getPreviousAudiobook(it)?.path }
        }
    }

    suspend fun getPlaybackSpeed(filePath: String): Float {
        return audiobookDao.getPlaybackSpeed(filePath) ?: 1.0f
    }


    fun updatePlaybackSpeed(filePath: String, playbackSpeed: Float) {
        serviceScope.launch {
            audiobookDao.updatePlaybackSpeed(filePath, playbackSpeed)
            if (filePath == currentFilePath) {
                mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(playbackSpeed)
            }
        }
    }


    private suspend fun updateLastReadTimestamp(filePath: String, setToBeginning: Boolean = false) {
        val currentPosition = if (setToBeginning) 0L else mediaPlayer.currentPosition.toLong()
        audiobookDao.updateLastReadTimestamp(filePath, currentPosition)
    }


    fun stopAudio() {
        serviceScope.launch {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastActionTimestamp < actionDebounceTime) {
                Log.d(TAG, "Ignoring rapid stop action")
                return@launch
            }
            lastActionTimestamp = currentTime

            if (playbackState == PlaybackState.COMPLETING) {
                Log.d(TAG, "Ignoring stop action during completion")
                return@launch
            }

            playbackState = PlaybackState.STOPPED

            mediaPlayer.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    currentFilePath?.let { updateLastReadTimestamp(it) }
                }
                player.stop()
            }
            updateNotification()
        }
    }

    fun getCurrentAudiobookInfo(callback: (AudiobookInfo?) -> Unit) {
        serviceScope.launch {
            val currentFilePath = currentFilePath
            if (currentFilePath != null) {
                val audiobook = getOrCreateAudiobook(currentFilePath)
                val isPlaying = mediaPlayer.isPlaying
                val currentPosition = mediaPlayer.currentPosition
                val duration = mediaPlayer.duration
                val percentageListened = if (duration > 0) ((currentPosition.toFloat() / duration) * 100).toInt() else 0
                val remainingTime = duration - currentPosition
                val playbackSpeed = getPlaybackSpeed(currentFilePath)

                val audiobookInfo = AudiobookInfo(
                    filePath = currentFilePath,
                    title = audiobook.title,
                    percentageListened = percentageListened,
                    currentPosition = currentPosition,
                    duration = duration,
                    remainingTime = remainingTime,
                    isPlaying = isPlaying,
                    playbackSpeed = playbackSpeed
                )

                withContext(Dispatchers.Main) {
                    callback(audiobookInfo)
                }
            } else {
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }

    data class AudiobookInfo(
        val filePath: String,
        val title: String,
        val percentageListened: Int,
        val currentPosition: Int,
        val duration: Int,
        val remainingTime: Int,
        val isPlaying: Boolean,
        val playbackSpeed: Float
    )

    fun requestPlaybackInfo(filePath: String, callback: (PlaybackInfo) -> Unit) {
        Log.d(TAG, "Playback info requested for: $filePath")
        getPlaybackInfo(filePath, callback)
    }

    private fun getPlaybackInfo(filePath: String, callback: (PlaybackInfo) -> Unit) {
        serviceScope.launch {
            val isCurrentAudiobook = filePath == currentFilePath
            val isPlaying = isCurrentAudiobook && mediaPlayer.isPlaying
            Log.d(TAG, "Getting playback info. Is current audiobook: $isCurrentAudiobook, Is playing: $isPlaying")

            val currentPosition = if (isCurrentAudiobook) {
                mediaPlayer.currentPosition
            } else {
                audiobookDao.getLastReadTimestamp(filePath)?.toInt() ?: 0
            }

            val duration = if (isCurrentAudiobook) {
                mediaPlayer.duration
            } else {
                getDuration(filePath).toInt()
            }

            val percentageListened = if (duration > 0) ((currentPosition.toFloat() / duration) * 100).toInt() else 0
            val remainingTime = duration - currentPosition

            val title = getAudiobookTitle(filePath)
            val playbackSpeed = getPlaybackSpeed(filePath)

            val playbackInfo = PlaybackInfo(
                percentageListened = percentageListened,
                currentPosition = currentPosition,
                duration = duration,
                remainingTime = remainingTime,
                isCurrentAudiobook = isCurrentAudiobook,
                isPlaying = isPlaying,
                title = title,
                playbackSpeed = playbackSpeed
            )

            Log.d(TAG, "Playback info created: $playbackInfo")

            withContext(Dispatchers.Main) {
                callback(playbackInfo)
            }
        }
    }


    private suspend fun getAudiobookTitle(filePath: String): String {
        return audiobookDao.getTitle(filePath) ?: "Unknown Title"
    }


    data class PlaybackInfo(
        val percentageListened: Int,
        val currentPosition: Int,
        val duration: Int,
        val remainingTime: Int,
        val isCurrentAudiobook: Boolean,
        val isPlaying: Boolean,
        val title: String,
        val playbackSpeed: Float
    )


    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopAudio()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf() // Stop the service
    }

    override fun onDestroy() {
        serviceScope.launch {
            currentFilePath?.let { updateLastReadTimestamp(it) }
        }.invokeOnCompletion {
            mediaPlayer.release()
            // Unregister the receiver only if it was registered
            if (isReceiverRegistered) {
                unregisterReceiver(bluetoothReceiver)
                isReceiverRegistered = false
            }
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }


    companion object {
        private const val TAG = "PlaybackService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AudiobookPlaybackChannel"
        const val ACTION_PLAY = "com.example.audiobookplayerbeta.PLAY"
        const val ACTION_PAUSE = "com.example.audiobookplayerbeta.PAUSE"
        const val ACTION_REWIND = "com.example.audiobookplayerbeta.REWIND"
        const val ACTION_FAST_FORWARD = "com.example.audiobookplayerbeta.FAST_FORWARD"
    }
}