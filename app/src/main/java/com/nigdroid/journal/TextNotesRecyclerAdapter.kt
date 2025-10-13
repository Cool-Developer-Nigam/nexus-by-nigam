package com.nigdroid.journal

import android.content.Context
import android.graphics.Color
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
            // Set title
            textNoteTitle.text = note.title.ifEmpty { "Untitled" }

            // Display content as HTML
            val htmlContent = android.text.Html.fromHtml(
                note.content,
                android.text.Html.FROM_HTML_MODE_COMPACT
            )
            textNoteContent.text = htmlContent

            // Set colors
            try {
                textNoteCard.setCardBackgroundColor(Color.parseColor(note.backgroundColor))
                textNoteTitle.setTextColor(Color.parseColor(note.textColor))
                textNoteContent.setTextColor(Color.parseColor(note.textColor))
            } catch (e: Exception) {
                // Use default colors if parsing fails
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
}