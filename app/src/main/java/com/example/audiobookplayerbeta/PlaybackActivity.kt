package com.example.audiobookplayerbeta

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale


class PlaybackActivity : AppCompatActivity(), PlaybackService.PlaybackCallback {

    private lateinit var timerIcon: ImageView
    private lateinit var remainingTime: TextView
    private var timerUpdateJob: Job? = null
    private lateinit var title: TextView
    private lateinit var bookPercentage: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeListened: TextView
    private lateinit var timeRemaining: TextView
    private lateinit var totalDuration: TextView
    private lateinit var playbackSpeedButton: Button
    private lateinit var bookmarkIcon: ImageView
    private lateinit var equaliserIcon: ImageView
    private lateinit var skipLeft: ImageView
    private lateinit var playPause: ImageView
    private lateinit var skipRight: ImageView
    private lateinit var rewind: ImageView
    private lateinit var menu: ImageView
    private lateinit var fastForward: ImageView
    private lateinit var amplify: ImageView
    private lateinit var bookmarks: ImageView
    private lateinit var settings: ImageView

    private var currentPath: String? = null
    private var playbackService: PlaybackService? = null
    private var bound = false
    private lateinit var fileManager: FileManager
    private var isUpdatingPlaybackInfo = false
    private lateinit var bookmarkManager: BookmarkManager
    private lateinit var sharedPrefs: SharedPreferencesManager
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val showBookmarksLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedPosition = result.data?.getLongExtra(BookmarkActivity.EXTRA_SELECTED_BOOKMARK_POSITION, -1L)
            if (selectedPosition != null && selectedPosition != -1L) {
                seekToBookmarkPosition(selectedPosition)
            }
        }
    }

    private val preferenceChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val rewindValue = intent.getIntExtra("rewind_value", 10)
            updateRewindFastForwardButtons(rewindValue)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlaybackService.PlaybackBinder
            playbackService = binder.getService()
            playbackService?.setPlaybackCallback(this@PlaybackActivity)
            bound = true
            currentPath?.let { path ->
                lifecycleScope.launch {
                    initAudiobook(path)
                    fetchPlaybackInfo(path)
                    checkIfAudiobookIsPlaying(path) { isPlaying ->
                        if (isPlaying) {
                            Log.d("PlaybackActivity", "serviceConnection starts the playback info update")
                            startPlaybackInfoUpdates(path)
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            bound = false
        }
    }

    override fun onStart() {
        super.onStart()
        updatePlaybackInfo()
    }

    private fun updatePlaybackInfo() {
        if (bound && playbackService != null) {
            playbackService?.getCurrentAudiobookInfo { audiobook ->
                if (audiobook != null) {
                    runOnUiThread {
                        currentPath = audiobook.filePath
                        updateUI(audiobook)
                        if (audiobook.isPlaying) {
                            startPlaybackInfoUpdates(audiobook.filePath)
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(audiobook: PlaybackService.AudiobookInfo) {
        title.text = audiobook.title
        bookPercentage.text = resources.getString(R.string.book_percentage, audiobook.percentageListened)
        timeRemaining.text = formatTime(audiobook.remainingTime)
        timeListened.text = formatTime(audiobook.currentPosition)
        totalDuration.text = formatTime(audiobook.duration)
        playbackSpeedButton.text = String.format(Locale.getDefault(), "%.1f", audiobook.playbackSpeed) + "x"
        playPause.setImageResource(if (audiobook.isPlaying) R.drawable.ic_stop else R.drawable.ic_play)
        seekBar.max = audiobook.duration
        seekBar.progress = audiobook.currentPosition
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bookmarkManager = BookmarkManager(this)
        sharedPrefs = SharedPreferencesManager(applicationContext)

        // Initialize all elements
        timerIcon = findViewById(R.id.timerIcon)
        remainingTime = findViewById(R.id.remainingTime)
        remainingTime.text = getString(R.string.timer_not_set)
        title = findViewById(R.id.title)
        bookPercentage = findViewById(R.id.bookPercentage)
        seekBar = findViewById(R.id.seekBar)
        timeListened = findViewById(R.id.timeListened)
        timeRemaining = findViewById(R.id.timeRemaining)
        totalDuration = findViewById(R.id.totalDuration)
        playbackSpeedButton = findViewById(R.id.playbackSpeedButton)
        bookmarkIcon = findViewById(R.id.bookmarkIcon)
        equaliserIcon = findViewById(R.id.equaliserIcon)
        skipLeft = findViewById(R.id.skipLeft)
        playPause = findViewById(R.id.playPause)
        skipRight = findViewById(R.id.skipRight)
        rewind = findViewById(R.id.rewind)
        menu = findViewById(R.id.menu)
        fastForward = findViewById(R.id.fastForward)
        amplify = findViewById(R.id.amp)
        bookmarks = findViewById(R.id.bookmarks)
        settings = findViewById(R.id.settings)

        updateRewindFastForwardButtons(sharedPrefs.rewindValue)

        remainingTime.text = "--:--:--"
        timerIcon.setImageResource(R.drawable.ic_timer_neutral)

        // Retrieve file path from the intent
        currentPath = intent.getStringExtra("FILE_PATH")

        // Bind to the PlaybackService
        Intent(this, PlaybackService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            refreshActivity()
        }


        fileManager = FileManager(this)

        updateRewindFastForwardButtons(sharedPrefs.rewindValue)

        // Register the BroadcastReceiver
        registerReceiver(
            preferenceChangeReceiver,
            IntentFilter("com.example.audiobookplayerbeta.PREFERENCE_CHANGED"),
            RECEIVER_NOT_EXPORTED
        )

        menu.setOnClickListener {
            openMenuActivity()
        }
        playPause.setOnClickListener {
            handlePlayPauseClick()
        }

        playbackSpeedButton.setOnClickListener {
            showPlaybackSpeedDialog()
        }

        equaliserIcon.setOnClickListener {
            showPitchAdjustmentDialog()
        }

        settings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        bookmarks.setOnClickListener {
            currentPath?.let { path ->
                val intent = Intent(this, BookmarkActivity::class.java).apply {
                    putExtra(BookmarkActivity.EXTRA_AUDIOBOOK_PATH, path)
                }
                showBookmarksLauncher.launch(intent)
            }
        }

        setupBookmarkCreation()

        setupSeekBar()
        setupSeekBarTouchListener()
        setupRewindFastForwardListeners()
        setupTimerIconClickListener()
        setupVolumeAdjustmentListeners()
    }

    private fun setupSeekBarTouchListener() {
        seekBar.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Disable SwipeRefreshLayout when the user touches the SeekBar
                    swipeRefreshLayout.isEnabled = false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Re-enable SwipeRefreshLayout when the user releases the SeekBar
                    swipeRefreshLayout.isEnabled = true
                }
            }
            false // Return false to allow the SeekBar to handle the touch event
        }
    }


    private fun refreshActivity() {
        // Perform cleanup
        cleanupResources()

        // Create a new intent to restart the activity
        val intent = Intent(this, PlaybackActivity::class.java).apply {
            putExtra("FILE_PATH", sharedPrefs.lastRead)
        }

        // Start the new activity
        startActivity(intent)

        // Finish the current activity
        finish()

        // Apply a transition animation (optional)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun cleanupResources() {
        // Stop ongoing processes
        stopPlaybackInfoUpdates()
        stopTimerUpdates()

        // Unbind from the service if bound
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }

        // Cancel any ongoing coroutines
        timerUpdateJob?.cancel()
        timerUpdateJob = null

        // Any other cleanup you need to perform
    }

    override fun onResume() {
        super.onResume()
        updateRewindFastForwardButtons(sharedPrefs.rewindValue)
    }

    private fun updateRewindFastForwardButtons(seconds: Int) {
        when (seconds) {
            5 -> {
                rewind.setImageResource(R.drawable.ic_rewind5seconds)
                fastForward.setImageResource(R.drawable.ic_skip5seconds)
            }
            10 -> {
                rewind.setImageResource(R.drawable.ic_rewind10seconds)
                fastForward.setImageResource(R.drawable.ic_skip10seconds)
            }
            30 -> {
                rewind.setImageResource(R.drawable.ic_rewind30seconds)
                fastForward.setImageResource(R.drawable.ic_skip30seconds)
            }
        }
    }

    private fun setupTimerIconClickListener() {
        timerIcon.setOnClickListener {
            if ((playbackService?.getTimerRemaining() ?: 0) > 0) {
                showCancelTimerDialog()
            } else {
                showSetTimerDialog()
            }
        }
    }

    private fun showSetTimerDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle("Set Timer")
            .setMessage("Enter time in minutes:")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val minutes = input.text.toString().toIntOrNull() ?: 0
                if (minutes > 0) {
                    playbackService?.setTimer(minutes)
                    startTimerUpdates()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCancelTimerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Timer")
            .setMessage("Do you want to cancel the current timer?")
            .setPositiveButton("Yes") { _, _ ->
                playbackService?.cancelTimer()
                stopTimerUpdates()
                lifecycleScope.launch {
                    delay(2000) // Show "off" icon for 2 seconds
                    timerIcon.setImageResource(R.drawable.ic_timer_neutral)
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun startTimerUpdates() {
        timerUpdateJob?.cancel() // Cancel any existing update job
        timerUpdateJob = lifecycleScope.launch {
            while (isActive) {
                val remaining = playbackService?.getTimerRemaining() ?: 0L
                if (remaining > 0) {
                    val hours = (remaining / 1000) / 3600
                    val minutes = ((remaining / 1000) % 3600) / 60
                    val seconds = (remaining / 1000) % 60
                    remainingTime.text = getString(R.string.timer_remaining,
                        String.format(Locale.getDefault(), "%02d", hours),
                        String.format(Locale.getDefault(), "%02d", minutes),
                        String.format(Locale.getDefault(), "%02d", seconds))
                    timerIcon.setImageResource(R.drawable.ic_timer_on)
                } else {
                    remainingTime.text = getString(R.string.timer_not_set)
                    timerIcon.setImageResource(R.drawable.ic_timer_neutral)
                    break
                }
                delay(1000) // Update every second
            }
        }
    }

    private fun stopTimerUpdates() {
        timerUpdateJob?.cancel()
        timerUpdateJob = null
        remainingTime.text = getString(R.string.timer_not_set)
        timerIcon.setImageResource(R.drawable.ic_timer_neutral)
    }


    private fun seekToBookmarkPosition(position: Long) {
        currentPath?.let { path ->
            lifecycleScope.launch {
                playbackService?.seekTo(position.toInt())
                updateTimeViews(position.toInt(), seekBar.max)
                // If the audiobook is not playing, start playback
                if (playbackService?.isAudiobookPlaying(path) != true) {
                    playAudiobook(path)
                }
                // Ensure UI updates are started
                startPlaybackInfoUpdates(path)
            }
        }
    }

    private fun setupBookmarkCreation() {
        bookmarkIcon.setOnClickListener {
            currentPath?.let { path ->
                lifecycleScope.launch {
                    val currentPosition = playbackService?.getCurrentPosition(path)
                    if (currentPosition != null) {
                        bookmarkManager.createBookmark(path, currentPosition.toLong())
                    } else {
                        Log.e("PlaybackActivity", "Failed to get current position for bookmark creation")
                        // Optionally, show a Toast to the user indicating the failure
                    }
                }
            }
        }
    }

    private fun openMenuActivity() {
        Intent(this, MenuActivity::class.java).apply {
            putExtra("FILE_PATH", fileManager.getParentDirectory(currentPath ?: ""))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(this)
        }
    }

    private fun showPlaybackSpeedDialog() {
        val dialogView = layoutInflater.inflate(R.layout.playback_speed_dialog, null)
        val decreaseSpeedButton: Button = dialogView.findViewById(R.id.decreaseSpeed)
        val increaseSpeedButton: Button = dialogView.findViewById(R.id.increaseSpeed)
        val currentPlaybackSpeedTextView: TextView = dialogView.findViewById(R.id.currentPlaybackSpeed)

        // Create the dialog
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Adjust Playback Speed")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Set initial playback speed in the TextView
        lifecycleScope.launch {
            currentPath?.let { path ->
                playbackService?.let { service ->
                    val playbackSpeed = service.getPlaybackSpeed(path)
                    currentPlaybackSpeedTextView.text = playbackSpeed.toString()
                }
            }
        }

        // Set up button click listeners
        decreaseSpeedButton.setOnClickListener {
            adjustPlaybackSpeed(-0.1f, currentPlaybackSpeedTextView)
        }

        increaseSpeedButton.setOnClickListener {
            adjustPlaybackSpeed(0.1f, currentPlaybackSpeedTextView)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun adjustPlaybackSpeed(change: Float, playbackSpeedTextView: TextView) {
        lifecycleScope.launch {
            currentPath?.let { path ->
                playbackService?.let { service ->
                    val currentSpeed = service.getPlaybackSpeed(path)
                    val newSpeed = (currentSpeed + change).coerceIn(0.5f, 2.0f)
                    val formattedSpeed = String.format("%.1f", newSpeed) // Format to one decimal place
                    playbackSpeedTextView.text = "${formattedSpeed}x" // Add "x" to indicate speed
                    service.updatePlaybackSpeed(path, formattedSpeed.toFloat()) // Update speed in the service
                }
            }
        }
    }

    private fun showPitchAdjustmentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.pitch_adjustment_dialog, null)
        val decreasePitchButton: Button = dialogView.findViewById(R.id.decreasePitch)
        val increasePitchButton: Button = dialogView.findViewById(R.id.increasePitch)
        val currentPitchTextView: TextView = dialogView.findViewById(R.id.currentPitch)

        // Create the dialog
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Adjust Pitch")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Set initial pitch in the TextView with correct formatting
        lifecycleScope.launch {
            currentPath?.let { path ->
                playbackService?.let { service ->
                    val pitch = service.getPlaybackPitch(path)
                    val formattedPitch = String.format(Locale.getDefault(),"%.1f", pitch) // Format to one decimal place
                    currentPitchTextView.text = "${formattedPitch}x" // Add "x" to indicate pitch
                }
            }
        }

        // Set up button click listeners
        decreasePitchButton.setOnClickListener {
            adjustPlaybackPitch(-0.1f, currentPitchTextView)
        }

        increasePitchButton.setOnClickListener {
            adjustPlaybackPitch(0.1f, currentPitchTextView)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun adjustPlaybackPitch(change: Float, pitchTextView: TextView) {
        lifecycleScope.launch {
            currentPath?.let { path ->
                playbackService?.let { service ->
                    val currentPitch = service.getPlaybackPitch(path)
                    val newPitch = (currentPitch + change).coerceIn(0.5f, 2.0f) // Example range
                    val formattedPitch = String.format("%.1f", newPitch) // Format to one decimal place
                    pitchTextView.text = "${formattedPitch}x" // Add "x" to indicate pitch
                    service.setPitch(path, newPitch) // Update pitch in the service
                }
            }
        }
    }

    private fun showVolumeAdjustmentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.amp_dialog, null)
        val decreaseAmpButton: Button = dialogView.findViewById(R.id.decreaseAmp)
        val increaseAmpButton: Button = dialogView.findViewById(R.id.increaseAmp)
        val currentAmpTextView: TextView = dialogView.findViewById(R.id.currentAmp)

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Adjust Volume")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        lifecycleScope.launch {
            currentPath?.let { path ->
                playbackService?.let { service ->
                    val amplificationLevel = service.getAmplificationLevel(path)
                    val formattedLevel = String.format(Locale.getDefault(),"%.1f", amplificationLevel) // Format to one decimal place
                    currentAmpTextView.text = "${formattedLevel}x" // Add "x" to indicate amplification level
                }
            }
        }

        decreaseAmpButton.setOnClickListener {
            adjustAmplificationLevel(-0.1f, currentAmpTextView)
        }

        increaseAmpButton.setOnClickListener {
            adjustAmplificationLevel(0.1f, currentAmpTextView)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun adjustAmplificationLevel(change: Float, ampTextView: TextView) {
        lifecycleScope.launch {
            currentPath?.let { path ->
                playbackService?.let { service ->
                    val currentLevel = service.getAmplificationLevel(path)
                    val newLevel = (currentLevel + change).coerceIn(1.0f, 2.0f) // Example range
                    val formattedLevel = String.format("%.1f", newLevel) // Format to one decimal place
                    ampTextView.text = "${formattedLevel}x" // Add "x" to indicate amplification level
                    service.setAmplificationLevel(path, newLevel) // Update amplification level in the service
                }
            }
        }
    }

    private fun setupVolumeAdjustmentListeners() {
        amplify.setOnClickListener {
            showVolumeAdjustmentDialog()
        }
    }

    override fun onNewAudiobookStarted(filePath: String) {
        Log.d("PlaybackActivity", "New audiobook started: $filePath")
        currentPath = filePath
        lifecycleScope.launch {
            stopPlaybackInfoUpdates()
            delay(1000)
            fetchPlaybackInfo(filePath)
            Log.d("PlaybackActivity", "onNewAudiobook starts the playback info update")
            startPlaybackInfoUpdates(filePath)

            // Add these lines to ensure the UI is updated
            runOnUiThread {
                playPause.setImageResource(R.drawable.ic_stop)
            }

            // Explicitly start playing the new audiobook
            playAudiobook(filePath, startFromBeginning = true)
        }
    }

    private fun skipToNextAudiobook() {
        playbackService?.skipToNextAudiobook()
    }

    private fun skipToPreviousAudiobook() {
        playbackService?.skipToPreviousAudiobook()
    }

    private fun fetchPlaybackInfo(filePath: String) {
        if (bound) {
            playbackService?.requestPlaybackInfo(filePath) { playbackInfo ->
                Log.d("PlaybackActivity", "Received playback info: $playbackInfo")
                runOnUiThread {
                    updateUI(playbackInfo)
                }
            }
        } else {
            Log.e("PlaybackActivity", "Service not bound when fetching playback info")
        }
    }

    private fun setupSeekBar() {
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                seekBar?.let {
                    currentPath?.let { path ->
                        checkIfAudiobookIsPlaying(path) { isPlaying ->
                            if (isPlaying) {
                                if (fromUser) {
                                    playbackService?.seekTo(progress)
                                    updateTimeViews(progress, it.max)
                                }
                            }
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                stopPlaybackInfoUpdates()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                currentPath?.let { path ->
                    startPlaybackInfoUpdates(path)
                }
            }
        })
    }

    private fun updateTimeViews(currentPosition: Int, duration: Int) {
        timeListened.text = formatTime(currentPosition)
        timeRemaining.text = formatTime(duration - currentPosition)
    }

    private fun checkIfAudiobookIsPlaying(filePath: String, callback: (Boolean) -> Unit) {
        if (bound) {
            lifecycleScope.launch {
                val isPlaying = playbackService?.isAudiobookPlaying(filePath) ?: false
                callback(isPlaying)
            }
        }
    }

    private fun handlePlayPauseClick() {
        currentPath?.let { path ->
            checkIfAudiobookIsPlaying(path) { isPlaying ->
                if (isPlaying) {
                    stopAudiobook()
                    stopPlaybackInfoUpdates()
                } else {
                    playAudiobook(path)
                    lifecycleScope.launch {
                        delay(500) // Give some time for playback to start
                        if (playbackService?.isAudiobookPlaying(path) == true) {
                            Log.d("PlaybackActivity", "handleplaypause starts the playback info update")
                            startPlaybackInfoUpdates(path)
                        } else {
                            Log.d("PlaybackActivity", "Audiobook didn't start playing")
                        }
                    }
                }
            }
        }
    }

    private fun startPlaybackInfoUpdates(filePath: String) {
        Log.d("PlaybackActivity", "Starting playback info updates for: $filePath")
        if (!isUpdatingPlaybackInfo) {
            isUpdatingPlaybackInfo = true
            lifecycleScope.launch {
                while (isUpdatingPlaybackInfo) {
                    Log.d("PlaybackActivity", "Fetching playback info for: $filePath")
                    fetchPlaybackInfo(filePath)
                    delay(1000) // Delay for 1 second
                }
                Log.d("PlaybackActivity", "Stopped updating playback info for: $filePath")
            }
        } else {
            Log.d("PlaybackActivity", "Playback info updates already running")
        }
    }

    private fun stopPlaybackInfoUpdates() {
        Log.d("PlaybackActivity", "Stopping playback info updates")
        isUpdatingPlaybackInfo = false
    }

    private fun stopAudiobook() {
        if (bound) {
            playbackService?.stopAudio()
            playPause.setImageResource(R.drawable.ic_play)
            stopPlaybackInfoUpdates()
        }
    }

    private fun playAudiobook(filePath: String, startFromBeginning: Boolean = false) {
        if (bound) {
            playbackService?.playAudio(filePath, startFromBeginning)
            playPause.setImageResource(R.drawable.ic_stop)
            startPlaybackInfoUpdates(filePath)  // Add this line to ensure updates start
        }
    }

    private suspend fun initAudiobook(filePath: String) {
        if (bound) {
            playbackService?.getOrCreateAudiobook(filePath)
        }
    }

    private fun setupRewindFastForwardListeners() {
        rewind.setOnClickListener {
            currentPath?.let { path ->
                playbackService?.rewind(path)
            }
        }

        fastForward.setOnClickListener {
            currentPath?.let { path ->
                playbackService?.fastForward(path)
            }
        }

        skipRight.setOnClickListener {
            skipToNextAudiobook()
        }

        skipLeft.setOnClickListener {
            skipToPreviousAudiobook()
        }
    }

    private fun updateUI(playbackInfo: PlaybackService.PlaybackInfo) {
        bookPercentage.text = resources.getString(R.string.book_percentage, playbackInfo.percentageListened)
        timeRemaining.text = formatTime(playbackInfo.remainingTime)
        timeListened.text = formatTime(playbackInfo.currentPosition)
        totalDuration.text = formatTime(playbackInfo.duration)
        title.text = playbackInfo.title
        playbackSpeedButton.text = String.format(Locale.getDefault(),"%.1f", playbackInfo.playbackSpeed) + "x"

        // Update play/pause button state
        playPause.setImageResource(if (playbackInfo.isPlaying) R.drawable.ic_stop else R.drawable.ic_play)
        seekBar.max = playbackInfo.duration
        seekBar.progress = playbackInfo.currentPosition

        Log.e("PlaybackActivity", "Updated UI: " +
                "Title=${playbackInfo.title}, " +
                "PercentageListened=${playbackInfo.percentageListened}%, " +
                "CurrentPosition=${formatTime(playbackInfo.currentPosition)}, " +
                "RemainingTime=${formatTime(playbackInfo.remainingTime)}, " +
                "TotalDuration=${formatTime(playbackInfo.duration)}, " +
                "PlaybackSpeed=${playbackInfo.playbackSpeed}, " +
                "IsPlaying=${playbackInfo.isPlaying}")
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(millis: Int): String {
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onDestroy() {
        cleanupResources()
        super.onDestroy()
    }
}
