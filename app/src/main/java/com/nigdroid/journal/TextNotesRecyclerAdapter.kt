package com.nigdroid.journal

import android.content.Context
import android.graphics.Color
import android.text.Html
import android.text.Spanned
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nigdroid.journal.databinding.ItemTextNoteBinding

class TextNotesRecyclerAdapter(
    private val context: Context,
    private val notesList: List<TextNote>,
    private val onItemClick: (TextNote) -> Unit
) : RecyclerView.Adapter<TextNotesRecyclerAdapter.TextNoteViewHolder>() {

    inner class TextNoteViewHolder(val binding: ItemTextNoteBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextNoteViewHolder {
        val binding = ItemTextNoteBinding.inflate(
            LayoutInflater.from(context),
            parent,
            false
        )
        return TextNoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TextNoteViewHolder, position: Int) {
        val note = notesList[position]

        with(holder.binding) {
            // Set title - strip HTML if present
            val titleText = if (note.title.contains("<")) {
                Html.fromHtml(note.title, Html.FROM_HTML_MODE_COMPACT).toString().trim()
            } else {
                note.title
            }
            textNoteTitle.text = titleText.ifEmpty { "Untitled" }

            // Display content - properly handle HTML
            val contentText = if (note.content.contains("<")) {
                // Convert HTML to Spanned text
                val spanned = Html.fromHtml(note.content, Html.FROM_HTML_MODE_COMPACT)
                // Remove leading/trailing whitespace
                spanned.trim()
            } else {
                note.content
            }

            textNoteContent.text = if (contentText.isEmpty()) {
                "No content"
            } else {
                contentText
            }

            // Set colors
            try {
                textNoteCard.setCardBackgroundColor(Color.parseColor(note.backgroundColor))
                textNoteTitle.setTextColor(Color.parseColor(note.textColor))
                textNoteContent.setTextColor(Color.parseColor(note.textColor))
            } catch (e: Exception) {
                // Use default colors if parsing fails
                textNoteCard.setCardBackgroundColor(Color.WHITE)
                textNoteTitle.setTextColor(Color.BLACK)
                textNoteContent.setTextColor(Color.BLACK)
            }

            // Pin icon visibility
            pinIcon.visibility = if (note.isPinned) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            // Timestamp
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                note.timeModified,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            textNoteTimestamp.text = relativeTime

            // Card click listener
            textNoteCard.setOnClickListener {
                onItemClick(note)
            }
        }
    }

    override fun getItemCount(): Int = notesList.size

    // Extension function to trim Spanned text
    private fun Spanned.trim(): CharSequence {
        var start = 0
        var end = length

        while (start < end && Character.isWhitespace(this[start])) {
            start++
        }

        while (end > start && Character.isWhitespace(this[end - 1])) {
            end--
        }

        return if (start > 0 || end < length) {
            subSequence(start, end)
        } else {
            this
        }
    }
}