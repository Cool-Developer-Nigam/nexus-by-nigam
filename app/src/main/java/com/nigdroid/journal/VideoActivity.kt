package com.nigdroid.journal

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout

class VideoActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var videoThumbnail: android.widget.ImageView
    private lateinit var btnPlayOverlay: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvVideoTitle: TextView
    private lateinit var cardContainer: androidx.cardview.widget.CardView
    private lateinit var mainLayout: ConstraintLayout

    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window transparent and show content behind
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Enable blur effect on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            )
            window.attributes.blurBehindRadius = 20
        }

        setContentView(R.layout.activity_video)

        initializeViews()
        setupClickListeners()
        setupDismissOnOutsideClick()
        setupVideoPlayer()
    }

    private fun initializeViews() {
        videoView = findViewById(R.id.videoView)
        videoThumbnail = findViewById(R.id.videoThumbnail)
        btnPlayOverlay = findViewById(R.id.btnPlayOverlay)
        seekBar = findViewById(R.id.seekBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnRewind = findViewById(R.id.btnRewind)
        btnForward = findViewById(R.id.btnForward)
        btnClose = findViewById(R.id.btnClose)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        tvVideoTitle = findViewById(R.id.tvVideoTitle)
        cardContainer = findViewById(R.id.cardContainer)
        mainLayout = findViewById(R.id.main)
    }

    private fun setupClickListeners() {
        // Close button
        btnClose.setOnClickListener {
            finish()
        }

        // Play overlay button (on thumbnail)
        btnPlayOverlay.setOnClickListener {
            startVideoPlayback()
        }

        // Thumbnail click
        videoThumbnail.setOnClickListener {
            startVideoPlayback()
        }

        // Play/Pause button
        btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        // Rewind 5 seconds
        btnRewind.setOnClickListener {
            val currentPosition = videoView.currentPosition
            val newPosition = maxOf(0, currentPosition - 5000) // 5 seconds = 5000ms
            videoView.seekTo(newPosition)
        }

        // Forward 5 seconds
        btnForward.setOnClickListener {
            val currentPosition = videoView.currentPosition
            val duration = videoView.duration
            val newPosition = minOf(duration, currentPosition + 5000) // 5 seconds = 5000ms
            videoView.seekTo(newPosition)
        }

        // SeekBar change listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    videoView.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(updateSeekBar)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                handler.post(updateSeekBar)
            }
        })
    }

    private fun setupDismissOnOutsideClick() {
        // Dismiss when clicking outside the card
        mainLayout.setOnClickListener {
            finish()
        }

        // Prevent clicks on the card from dismissing
        cardContainer.setOnClickListener {
            // Do nothing - consume the click
        }
    }

    private fun setupVideoPlayer() {
        // Get video URI and thumbnail from intent
        val videoUri = intent.getStringExtra("VIDEO_URI")
        val videoTitle = intent.getStringExtra("VIDEO_TITLE")
        val thumbnailUrl = intent.getStringExtra("THUMBNAIL_URL")

        if (videoTitle != null) {
            tvVideoTitle.text = videoTitle
        } else {
            tvVideoTitle.text = "Video Player"
        }

        // Load thumbnail if provided
        if (thumbnailUrl != null) {
            loadThumbnail(thumbnailUrl)
        } else {
            // Show default thumbnail or hide it
            videoThumbnail.setImageResource(android.R.drawable.ic_media_play)
        }

        if (videoUri != null) {
            videoView.setVideoURI(Uri.parse(videoUri))
        } else {
            // Default test video if no URI provided
            val defaultVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            videoView.setVideoURI(Uri.parse(defaultVideoUrl))
            tvVideoTitle.text = "Sample Video"
        }

        // Video prepared listener
        videoView.setOnPreparedListener { mediaPlayer ->
            val duration = videoView.duration
            seekBar.max = duration
            tvTotalTime.text = formatTime(duration)

            // Don't auto-play, wait for user to click
            // User needs to click thumbnail or play button to start
        }

        // Video completion listener
        videoView.setOnCompletionListener {
            isPlaying = false
            updatePlayPauseButton()
            handler.removeCallbacks(updateSeekBar)
            seekBar.progress = 0
            tvCurrentTime.text = "00:00"

            // Show thumbnail again
            videoThumbnail.visibility = android.view.View.VISIBLE
            btnPlayOverlay.visibility = android.view.View.VISIBLE
        }

        // Error listener
        videoView.setOnErrorListener { _, what, extra ->
            // Handle error
            finish()
            true
        }
    }

    private fun loadThumbnail(thumbnailUrl: String) {
        // Using Glide to load thumbnail (make sure Glide is in your dependencies)
        try {
            com.bumptech.glide.Glide.with(this)
                .load(thumbnailUrl)
                .placeholder(android.R.drawable.ic_media_play)
                .error(android.R.drawable.ic_media_play)
                .centerCrop()
                .into(videoThumbnail)
        } catch (e: Exception) {
            // If Glide is not available, use default
            videoThumbnail.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun startVideoPlayback() {
        // Hide thumbnail and play button overlay
        videoThumbnail.visibility = android.view.View.GONE
        btnPlayOverlay.visibility = android.view.View.GONE

        // Start video
        videoView.start()
        isPlaying = true
        updatePlayPauseButton()
        handler.post(updateSeekBar)
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            videoView.pause()
            isPlaying = false
            handler.removeCallbacks(updateSeekBar)
        } else {
            videoView.start()
            isPlaying = true
            handler.post(updateSeekBar)
        }
        updatePlayPauseButton()
    }

    private fun updatePlayPauseButton() {
        if (isPlaying) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private val updateSeekBar = object : Runnable {
        override fun run() {
            if (isPlaying) {
                val currentPosition = videoView.currentPosition
                seekBar.progress = currentPosition
                tvCurrentTime.text = formatTime(currentPosition)
                handler.postDelayed(this, 100) // Update every 100ms
            }
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = (milliseconds / (1000 * 60 * 60))

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isPlaying) {
            videoView.pause()
            handler.removeCallbacks(updateSeekBar)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isPlaying) {
            videoView.start()
            handler.post(updateSeekBar)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBar)
        videoView.stopPlayback()
    }
}