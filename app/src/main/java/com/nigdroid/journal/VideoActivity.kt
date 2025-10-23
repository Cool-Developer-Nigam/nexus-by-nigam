package com.nigdroid.journal

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var progressBar: android.widget.ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var isPrepared = false
    private var shouldAutoPlay = false
    private var currentPosition = 0

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
        progressBar = findViewById(R.id.progressBar)
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
            val newPosition = maxOf(0, currentPosition - 5000)
            videoView.seekTo(newPosition)
        }

        // Forward 5 seconds
        btnForward.setOnClickListener {
            val currentPosition = videoView.currentPosition
            val duration = videoView.duration
            val newPosition = minOf(duration, currentPosition + 5000)
            videoView.seekTo(newPosition)
        }

        // SeekBar change listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && isPrepared) {
                    videoView.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(updateSeekBar)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (isPlaying) {
                    handler.post(updateSeekBar)
                }
            }
        })
    }

    private fun setupDismissOnOutsideClick() {
        mainLayout.setOnClickListener {
            finish()
        }

        cardContainer.setOnClickListener {
            // Consume click
        }
    }

    private fun setupVideoPlayer() {
        val videoUri = intent.getStringExtra("VIDEO_URI")
        val videoTitle = intent.getStringExtra("VIDEO_TITLE")
        val thumbnailUrl = intent.getStringExtra("THUMBNAIL_URL")

        Log.d("VideoActivity", "Video URI: $videoUri")
        Log.d("VideoActivity", "Thumbnail URL: $thumbnailUrl")

        if (videoTitle != null) {
            tvVideoTitle.text = videoTitle
        } else {
            tvVideoTitle.text = "Video Player"
        }

        if (thumbnailUrl != null) {
            loadThumbnail(thumbnailUrl)
        } else {
            videoThumbnail.setImageResource(android.R.drawable.ic_media_play)
        }

        if (videoUri != null) {
            if (videoUri.startsWith("gs://")) {
                Log.w("VideoActivity", "gs:// URL detected, converting to download URL")
                convertFirebaseUrl(videoUri)
            } else if (videoUri.startsWith("http://") || videoUri.startsWith("https://")) {
                setVideoUri(videoUri)
            } else {
                Log.e("VideoActivity", "Invalid video URI format: $videoUri")
                Toast.makeText(this, "Invalid video URL format", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            val defaultVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            setVideoUri(defaultVideoUrl)
            tvVideoTitle.text = "Sample Video"
        }

        setupVideoListeners()
    }

    private fun setupVideoListeners() {
        // Video prepared listener
        videoView.setOnPreparedListener { mediaPlayer ->
            isPrepared = true
            val duration = videoView.duration
            seekBar.max = duration
            tvTotalTime.text = formatTime(duration)

            progressBar.visibility = android.view.View.GONE

            Log.d("VideoActivity", "Video prepared. Duration: $duration")

            if (currentPosition > 0) {
                videoView.seekTo(currentPosition)
                currentPosition = 0
            }

            if (shouldAutoPlay) {
                videoView.start()
                isPlaying = true
                updatePlayPauseButton()
                handler.post(updateSeekBar)
                handler.postDelayed(bufferingCheck, 100)
                shouldAutoPlay = false
            }
        }

        // Video completion listener
        videoView.setOnCompletionListener {
            isPlaying = false
            isPrepared = false
            updatePlayPauseButton()
            handler.removeCallbacks(updateSeekBar)
            handler.removeCallbacks(bufferingCheck)
            seekBar.progress = 0
            tvCurrentTime.text = "00:00"

            videoThumbnail.visibility = android.view.View.VISIBLE
            btnPlayOverlay.visibility = android.view.View.VISIBLE
            progressBar.visibility = android.view.View.GONE

            Log.d("VideoActivity", "Video completed")
        }

        // Error listener
        videoView.setOnErrorListener { _, what, extra ->
            Log.e("VideoActivity", "Video error - what: $what, extra: $extra")
            progressBar.visibility = android.view.View.GONE
            Toast.makeText(this, "Error playing video", Toast.LENGTH_SHORT).show()
            finish()
            true
        }

        // Info listener for buffering
        videoView.setOnInfoListener { _, what, extra ->
            when (what) {
                android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    Log.d("VideoActivity", "Buffering started")
                    progressBar.visibility = android.view.View.VISIBLE
                }
                android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    Log.d("VideoActivity", "Buffering ended")
                    progressBar.visibility = android.view.View.GONE
                }
            }
            false
        }
    }

    private fun convertFirebaseUrl(gsUrl: String) {
        try {
            val path = gsUrl.removePrefix("gs://").substringAfter("/")
            val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance()
                .reference.child(path)

            storageRef.downloadUrl.addOnSuccessListener { uri ->
                Log.d("VideoActivity", "Converted to download URL: $uri")
                setVideoUri(uri.toString())
            }.addOnFailureListener { exception ->
                Log.e("VideoActivity", "Failed to get download URL", exception)
                Toast.makeText(this, "Failed to load video from Firebase", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e("VideoActivity", "Error converting Firebase URL", e)
            Toast.makeText(this, "Error loading video", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setVideoUri(videoUrl: String) {
        try {
            Log.d("VideoActivity", "Setting video URI: $videoUrl")

            if (videoUrl.contains("firebasestorage.googleapis.com")) {
                val uri = Uri.parse(videoUrl)
                val headers = HashMap<String, String>()
                headers["Accept-Encoding"] = "identity"
                videoView.setVideoURI(uri, headers)
            } else {
                videoView.setVideoURI(Uri.parse(videoUrl))
            }
        } catch (e: Exception) {
            Log.e("VideoActivity", "Error setting video URI", e)
            Toast.makeText(this, "Error loading video", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadThumbnail(thumbnailUrl: String) {
        try {
            com.bumptech.glide.Glide.with(this)
                .load(thumbnailUrl)
                .placeholder(android.R.drawable.ic_media_play)
                .error(android.R.drawable.ic_media_play)
                .centerCrop()
                .into(videoThumbnail)
        } catch (e: Exception) {
            Log.e("VideoActivity", "Error loading thumbnail with Glide", e)
            videoThumbnail.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun startVideoPlayback() {
        Log.d("VideoActivity", "startVideoPlayback called. isPrepared: $isPrepared")

        videoThumbnail.visibility = android.view.View.GONE
        btnPlayOverlay.visibility = android.view.View.GONE

        if (isPrepared) {
            videoView.start()
            isPlaying = true
            updatePlayPauseButton()
            handler.post(updateSeekBar)
            handler.postDelayed(bufferingCheck, 100)
        } else {
            shouldAutoPlay = true
            isPlaying = true
            updatePlayPauseButton()
            progressBar.visibility = android.view.View.VISIBLE
            Toast.makeText(this, "Loading video...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayPause() {
        if (!isPrepared) {
            startVideoPlayback()
            return
        }

        if (isPlaying) {
            videoView.pause()
            isPlaying = false
            handler.removeCallbacks(updateSeekBar)
            handler.removeCallbacks(bufferingCheck)
        } else {
            videoView.start()
            isPlaying = true
            handler.post(updateSeekBar)
            handler.postDelayed(bufferingCheck, 100)
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
            if (isPlaying && isPrepared) {
                try {
                    val currentPosition = videoView.currentPosition
                    seekBar.progress = currentPosition
                    tvCurrentTime.text = formatTime(currentPosition)
                    handler.postDelayed(this, 100)
                } catch (e: Exception) {
                    Log.e("VideoActivity", "Error updating seekbar", e)
                    handler.removeCallbacks(this)
                }
            }
        }
    }

    private var lastPosition = -1
    private var stallCount = 0
    private val bufferingCheck = object : Runnable {
        override fun run() {
            if (isPlaying && isPrepared) {
                try {
                    val current = videoView.currentPosition

                    if (current == lastPosition && current < videoView.duration - 100) {
                        stallCount++
                        Log.d("VideoActivity", "Video stalled. Count: $stallCount, Position: $current")

                        if (stallCount > 2) {
                            progressBar.visibility = android.view.View.VISIBLE
                        }

                        if (stallCount > 50) {
                            Log.w("VideoActivity", "Video stalled for too long, attempting to resume")
                            currentPosition = current
                            videoView.pause()
                            videoView.resume()
                            stallCount = 0
                        }
                    } else {
                        stallCount = 0
                        progressBar.visibility = android.view.View.GONE
                    }

                    lastPosition = current
                    handler.postDelayed(this, 100)
                } catch (e: Exception) {
                    Log.e("VideoActivity", "Error in buffering check", e)
                    handler.removeCallbacks(this)
                }
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
            currentPosition = videoView.currentPosition
            videoView.pause()
            handler.removeCallbacks(updateSeekBar)
            handler.removeCallbacks(bufferingCheck)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isPlaying && isPrepared) {
            if (currentPosition > 0) {
                videoView.seekTo(currentPosition)
            }
            videoView.start()
            handler.post(updateSeekBar)
            handler.postDelayed(bufferingCheck, 100)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBar)
        handler.removeCallbacks(bufferingCheck)
        try {
            videoView.stopPlayback()
        } catch (e: Exception) {
            Log.e("VideoActivity", "Error stopping playback", e)
        }
    }
}