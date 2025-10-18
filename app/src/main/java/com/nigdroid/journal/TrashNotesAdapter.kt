package com.nigdroid.journal

import android.content.Context
import android.graphics.Color
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class TrashNotesAdapter(
    private val context: Context,
    private var trashedItems: MutableList<TrashedItem>,
    private val onRestoreClick: (TrashedItem) -> Unit,
    private val onDeleteClick: (TrashedItem) -> Unit
) : RecyclerView.Adapter<TrashNotesAdapter.TrashViewHolder>() {

    companion object {
        private const val TAG = "TrashNotesAdapter"
    }

    inner class TrashViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.trashCard)
        val title: TextView = itemView.findViewById(R.id.trashTitle)
        val preview: TextView = itemView.findViewById(R.id.trashPreview)
        val daysRemaining: TextView = itemView.findViewById(R.id.daysRemaining)
        val btnRestore: ImageButton = itemView.findViewById(R.id.btnRestore)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeletePermanently)
        val typeLabel: TextView = itemView.findViewById(R.id.typeLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        Log.d(TAG, "onCreateViewHolder called")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trash, parent, false)
        return TrashViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder called for position $position")
        val item = trashedItems[position]

        Log.d(TAG, "Binding item: type=${item.type}, id=${item.id}")

        val titleText = when (item.type) {
            "journal" -> item.originalData["title"] as? String ?: "Untitled Journal"
            "textNote" -> item.originalData["title"] as? String ?: "Untitled Note"
            "todo" -> item.originalData["title"] as? String ?: "Untitled Checklist"
            "audio" -> item.originalData["title"] as? String ?: "Untitled Recording"
            else -> "Untitled"
        }

        holder.title.text = if (titleText.contains("<")) {
            Html.fromHtml(titleText, Html.FROM_HTML_MODE_COMPACT).toString().trim()
        } else {
            titleText
        }

        val previewText = when (item.type) {
            "journal" -> item.originalData["thoughts"] as? String
            "textNote" -> item.originalData["content"] as? String
            "audio" -> item.originalData["transcription"] as? String
            else -> null
        }

        if (previewText != null && previewText.isNotEmpty()) {
            holder.preview.visibility = View.VISIBLE
            holder.preview.text = if (previewText.contains("<")) {
                Html.fromHtml(previewText, Html.FROM_HTML_MODE_COMPACT).toString().trim()
            } else {
                previewText
            }
        } else {
            holder.preview.visibility = View.GONE
        }

        holder.typeLabel.text = when (item.type) {
            "journal" -> "Journal"
            "textNote" -> "Note"
            "todo" -> "Checklist"
            "audio" -> "Recording"
            else -> ""
        }

        val days = item.calculateDaysRemaining()
        holder.daysRemaining.text = when (days) {
            0 -> "Deletes today"
            1 -> "Deletes in 1 day"
            else -> "Deletes in $days days"
        }

        if (days <= 1) {
            holder.daysRemaining.setTextColor(Color.parseColor("#D32F2F"))
        } else {
            holder.daysRemaining.setTextColor(Color.parseColor("#757575"))
        }

        if (item.type == "textNote") {
            val bgColor = item.originalData["backgroundColor"] as? String
            val textColor = item.originalData["textColor"] as? String

            try {
                if (bgColor != null) {
                    holder.card.setCardBackgroundColor(Color.parseColor(bgColor))
                }
                if (textColor != null) {
                    holder.title.setTextColor(Color.parseColor(textColor))
                    holder.preview.setTextColor(Color.parseColor(textColor))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting colors: ${e.message}")
            }
        } else {
            holder.card.setCardBackgroundColor(Color.WHITE)
            holder.title.setTextColor(Color.BLACK)
            holder.preview.setTextColor(Color.GRAY)
        }

        holder.btnRestore.setOnClickListener {
            Log.d(TAG, "Restore clicked for item: ${item.id}")
            onRestoreClick(item)
        }

        holder.btnDelete.setOnClickListener {
            Log.d(TAG, "Delete clicked for item: ${item.id}")
            onDeleteClick(item)
        }
    }

    override fun getItemCount(): Int {
        Log.d(TAG, "getItemCount: ${trashedItems.size}")
        return trashedItems.size
    }

    fun updateList(newList: List<TrashedItem>) {
        Log.d(TAG, "updateList called with ${newList.size} items")
        Log.d(TAG, "Items to add: $newList")

        trashedItems.clear()
        trashedItems.addAll(newList)

        Log.d(TAG, "After update - trashedItems.size: ${trashedItems.size}")
        Log.d(TAG, "Calling notifyDataSetChanged()")

        notifyDataSetChanged()

        Log.d(TAG, "notifyDataSetChanged() completed")
    }
}