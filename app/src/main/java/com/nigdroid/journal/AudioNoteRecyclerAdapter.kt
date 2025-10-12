package com.nigdroid.journal

import android.content.Context
import android.media.MediaPlayer
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class AudioNotesRecyclerAdapter(
    private val context: Context,
    private val notesList: List<AudioNote>,
    private val onItemClick: (AudioNote) -> Unit
) : RecyclerView.Adapter<AudioNotesRecyclerAdapter.AudioNoteViewHolder>() {

    private var currentlyPlaying: MediaPlayer? = null
    private var currentPlayingPosition = -1

    inner class AudioNoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val noteCard: MaterialCardView = itemView.findViewById(R.id.audioNoteCard)
        val noteTitle: TextView = itemView.findViewById(R.id.audioNoteTitle)
        val pinIcon: ImageView = itemView.findViewById(R.id.pinIcon)
        val audioDuration: TextView = itemView.findViewById(R.id.audioDuration)
        val transcriptionPreview: TextView = itemView.findViewById(R.id.transcriptionPreview)
        val timestamp: TextView = itemView.findViewById(R.id.audioNoteTimestamp)
        val btnPlayPreview: ImageButton = itemView.findViewById(R.id.btnPlayPreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioNoteViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_audio_note, parent, false)
        return AudioNoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioNoteViewHolder, position: Int) {
        val note = notesList[position]

        holder.noteTitle.text = note.title.ifEmpty { "Untitled" }
        holder.pinIcon.visibility = if (note.isPinned) View.VISIBLE else View.GONE

        // Format duration
        val minutes = (note.audioDuration / 1000) / 60
        val seconds = (note.audioDuration / 1000) % 60
        holder.audioDuration.text = String.format("%02d:%02d", minutes, seconds)

        // Transcription preview
        if (note.transcription.isNotEmpty()) {
            holder.transcriptionPreview.visibility = View.VISIBLE
            holder.transcriptionPreview.text = note.transcription
        } else {
            holder.transcriptionPreview.visibility = View.GONE
        }

        // Timestamp
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            note.timeModified,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.timestamp.text = relativeTime

        // Play button
        holder.btnPlayPreview.setOnClickListener {
            if (currentPlayingPosition == position) {
                stopAudio(holder)
            } else {
                playAudio(note, holder, position)
            }
        }

        // Update play button icon
        if (currentPlayingPosition == position) {
            holder.btnPlayPreview.setImageResource(R.drawable.ic_pause)
        } else {
            holder.btnPlayPreview.setImageResource(R.drawable.ic_play)
        }

        // Card click
        holder.noteCard.setOnClickListener {
            onItemClick(note)
        }
    }

    private fun playAudio(note: AudioNote, holder: AudioNoteViewHolder, position: Int) {
        // Stop current if playing
        stopCurrentAudio()

        try {
            currentlyPlaying = MediaPlayer().apply {
                setDataSource(note.audioUrl)
                prepareAsync()
                setOnPreparedListener {
                    start()
                    currentPlayingPosition = position
                    holder.btnPlayPreview.setImageResource(R.drawable.ic_pause)
                }
                setOnCompletionListener {
                    stopAudio(holder)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAudio(holder: AudioNoteViewHolder) {
        currentlyPlaying?.apply {
            stop()
            release()
        }
        currentlyPlaying = null
        currentPlayingPosition = -1
        holder.btnPlayPreview.setImageResource(R.drawable.ic_play)
    }

    private fun stopCurrentAudio() {
        currentlyPlaying?.apply {
            stop()
            release()
        }
        currentlyPlaying = null
        if (currentPlayingPosition != -1) {
            notifyItemChanged(currentPlayingPosition)
        }
        currentPlayingPosition = -1
    }

    override fun getItemCount(): Int = notesList.size

    fun releasePlayer() {
        currentlyPlaying?.release()
        currentlyPlaying = null
    }
}