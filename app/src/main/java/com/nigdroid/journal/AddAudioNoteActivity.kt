package com.nigdroid.journal

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.nigdroid.journal.databinding.ActivityAddAudioNoteBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AddAudioNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddAudioNoteBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFilePath: String? = null
    private var audioUrl: String? = null
    private var audioDuration: Long = 0L

    private var isRecording = false
    private var isPlaying = false

    private var audioNoteId: String? = null
    private var isPinned = false
    private var isEditMode = false

    private var recordingStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var updateTimerRunnable: Runnable? = null

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_audio_note)

        firebaseAuth = FirebaseAuth.getInstance()

        audioNoteId = intent.getStringExtra("AUDIO_NOTE_ID")
        isEditMode = audioNoteId != null

        setupToolbar()
        setupClickListeners()

        if (isEditMode) {
            loadAudioNoteData()
        }

        checkAudioPermission()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnPin.setOnClickListener {
            isPinned = !isPinned
            updatePinIcon()
        }

        isPinned = intent.getBooleanExtra("IS_PINNED", false)
        updatePinIcon()
    }

    private fun updatePinIcon() {
        if (isPinned) {
            binding.btnPin.setImageResource(R.drawable.ic_pin)
        } else {
            binding.btnPin.setImageResource(R.drawable.ic_pin_outline)
        }
    }

    private fun setupClickListeners() {
        binding.btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        binding.btnPlay.setOnClickListener {
            if (isPlaying) {
                stopPlaying()
            } else {
                playAudio()
            }
        }

        binding.btnSave.setOnClickListener {
            saveAudioNote()
        }

        binding.btnDelete.setOnClickListener {
            if (isEditMode) {
                showDeleteConfirmationDialog()
            } else {
                finish()
            }
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio recording permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            checkAudioPermission()
            return
        }

        try {
            val audioFile = File(externalCacheDir, "audio_${System.currentTimeMillis()}.3gp")
            audioFilePath = audioFile.absolutePath

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFilePath)
                prepare()
                start()
            }

            isRecording = true
            recordingStartTime = System.currentTimeMillis()

            binding.btnRecord.setImageResource(R.drawable.ic_stop)
            binding.recordingIndicator.visibility = View.VISIBLE
            binding.audioWaveform.visibility = View.VISIBLE

            startTimer()

            Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show()

        } catch (e: IOException) {
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            isRecording = false
            audioDuration = System.currentTimeMillis() - recordingStartTime

            binding.btnRecord.setImageResource(R.drawable.ic_mic)
            binding.recordingIndicator.visibility = View.GONE
            binding.btnPlay.isEnabled = true

            stopTimer()

            Toast.makeText(this, "Recording stopped. Converting to text...", Toast.LENGTH_SHORT).show()

            convertSpeechToText()

        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun convertSpeechToText() {
        audioFilePath?.let { path ->
            lifecycleScope.launch {
                try {
                    binding.transcriptionProgress.visibility = View.VISIBLE
                    binding.tvTranscriptionStatus.visibility = View.VISIBLE
                    binding.tvTranscriptionStatus.text = "Converting speech to text..."
                    binding.etTranscription.setText("Processing...")

                    val transcription = performSpeechToText(path)

                    binding.transcriptionProgress.visibility = View.GONE
                    binding.tvTranscriptionStatus.visibility = View.GONE

                    if (transcription.isNotEmpty()) {
                        binding.etTranscription.setText(transcription)
                        Toast.makeText(
                            this@AddAudioNoteActivity,
                            "Transcription completed!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        binding.etTranscription.setText("")
                        Toast.makeText(
                            this@AddAudioNoteActivity,
                            "No speech detected. Add transcription manually.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                } catch (e: Exception) {
                    binding.transcriptionProgress.visibility = View.GONE
                    binding.tvTranscriptionStatus.visibility = View.GONE
                    binding.etTranscription.setText("")

                    Toast.makeText(
                        this@AddAudioNoteActivity,
                        "Transcription error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private suspend fun performSpeechToText(audioFilePath: String): String = withContext(Dispatchers.IO) {
        try {
            // Get access token from JSON credentials
            val credentialsStream = resources.openRawResource(R.raw.google_credentials)
            val credentials = GoogleCredentials.fromStream(credentialsStream)
                .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

            credentials.refreshIfExpired()
            val accessToken = credentials.accessToken.tokenValue

            // Read audio file and encode to base64
            val audioFile = File(audioFilePath)
            val audioBytes = audioFile.readBytes()
            val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            // Create request JSON
            val requestJson = """
                {
                    "config": {
                        "encoding": "AMR",
                        "sampleRateHertz": 8000,
                        "languageCode": "en-US",
                        "enableAutomaticPunctuation": true,
                        "model": "default"
                    },
                    "audio": {
                        "content": "$audioBase64"
                    }
                }
            """.trimIndent()

            // Make HTTP request with OAuth token
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://speech.googleapis.com/v1/speech:recognize")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                throw Exception("API Error: ${response.code} - $responseBody")
            }

            // Parse response
            val gson = Gson()
            val speechResponse = gson.fromJson(responseBody, SpeechResponse::class.java)

            // Extract transcription
            val transcription = StringBuilder()
            speechResponse.results?.forEach { result ->
                result.alternatives?.firstOrNull()?.transcript?.let {
                    transcription.append(it).append(" ")
                }
            }

            return@withContext transcription.toString().trim()

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // Data classes for JSON parsing
    data class SpeechResponse(
        @SerializedName("results")
        val results: List<Result>?
    )

    data class Result(
        @SerializedName("alternatives")
        val alternatives: List<Alternative>?
    )

    data class Alternative(
        @SerializedName("transcript")
        val transcript: String?,
        @SerializedName("confidence")
        val confidence: Double?
    )

    private fun playAudio() {
        val path = audioFilePath ?: audioUrl
        if (path == null) {
            Toast.makeText(this, "No audio to play", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                if (path.startsWith("http")) {
                    setDataSource(path)
                } else {
                    setDataSource(path)
                }
                prepare()
                start()
            }

            isPlaying = true
            binding.btnPlay.setImageResource(R.drawable.ic_pause)

            mediaPlayer?.setOnCompletionListener {
                stopPlaying()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error playing audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlaying() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        isPlaying = false
        binding.btnPlay.setImageResource(R.drawable.ic_play)
    }

    private fun startTimer() {
        updateTimerRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                val minutes = (elapsed / 1000) / 60
                val seconds = (elapsed / 1000) % 60
                binding.tvRecordingTime.text = String.format("%02d:%02d", minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateTimerRunnable!!)
    }

    private fun stopTimer() {
        updateTimerRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun loadAudioNoteData() {
        audioNoteId?.let { id ->
            db.collection("AudioNotes").document(id).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val note = document.toObject(AudioNote::class.java)
                        note?.let {
                            binding.etTitle.setText(it.title)
                            binding.etTranscription.setText(it.transcription)
                            audioUrl = it.audioUrl
                            audioDuration = it.audioDuration
                            isPinned = it.isPinned
                            updatePinIcon()

                            binding.btnPlay.isEnabled = true

                            val minutes = (it.audioDuration / 1000) / 60
                            val seconds = (it.audioDuration / 1000) % 60
                            binding.tvRecordingTime.text = String.format("%02d:%02d", minutes, seconds)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading note: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveAudioNote() {
        val title = binding.etTitle.text.toString().trim()
        val transcription = binding.etTranscription.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, "Please add a title", Toast.LENGTH_SHORT).show()
            return
        }

        if (audioFilePath == null && audioUrl == null) {
            Toast.makeText(this, "Please record audio", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        if (audioFilePath != null && audioUrl == null) {
            uploadAudioToStorage()
        } else {
            saveToFirestore(audioUrl ?: "")
        }
    }

    private fun uploadAudioToStorage() {
        val user = firebaseAuth.currentUser ?: return
        val audioFile = File(audioFilePath!!)
        val audioRef = storage.reference.child("audio_notes/${user.uid}/${System.currentTimeMillis()}.3gp")

        audioRef.putFile(Uri.fromFile(audioFile))
            .addOnSuccessListener { taskSnapshot ->
                audioRef.downloadUrl.addOnSuccessListener { uri ->
                    audioUrl = uri.toString()
                    saveToFirestore(audioUrl!!)
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveToFirestore(audioUrl: String) {
        val user = firebaseAuth.currentUser ?: return
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val username = sharedPref.getString("username", "Anonymous") ?: "Anonymous"

        val currentTime = System.currentTimeMillis()
        val title = binding.etTitle.text.toString().trim()
        val transcription = binding.etTranscription.text.toString().trim()

        val audioNote = AudioNote(
            id = audioNoteId ?: "",
            title = title,
            audioUrl = audioUrl,
            audioDuration = audioDuration,
            transcription = transcription,
            userId = user.uid,
            username = username,
            timeAdded = if (isEditMode) intent.getLongExtra("TIME_ADDED", currentTime) else currentTime,
            timeModified = currentTime,
            isPinned = isPinned
        )

        if (isEditMode && audioNoteId != null) {
            db.collection("AudioNotes").document(audioNoteId!!)
                .set(audioNote)
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Audio note updated", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            db.collection("AudioNotes")
                .add(audioNote)
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Audio note saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Audio Note")
            .setMessage("Are you sure you want to delete this audio note?")
            .setPositiveButton("Delete") { _, _ ->
                deleteAudioNote()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAudioNote() {
        audioNoteId?.let { id ->
            db.collection("AudioNotes").document(id)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Audio note deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaPlayer?.release()
        stopTimer()
    }
}