package com.nigdroid.journal

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class TodoRecyclerAdapter(
    private val context: Context,
    private val todoList: List<TodoItem>,
    private val onItemClick: (TodoItem) -> Unit
) : RecyclerView.Adapter<TodoRecyclerAdapter.TodoViewHolder>() {

    private val maxPreviewItems = 3

    inner class TodoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val todoCard: MaterialCardView = itemView.findViewById(R.id.todoCard)
        val todoTitle: TextView = itemView.findViewById(R.id.todoTitle)
        val pinIcon: ImageView = itemView.findViewById(R.id.pinIcon)
        val checklistContainer: LinearLayout = itemView.findViewById(R.id.checklistItemsContainer)
        val moreItemsText: TextView = itemView.findViewById(R.id.moreItemsText)
        val todoTimestamp: TextView = itemView.findViewById(R.id.todoTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_todo, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todo = todoList[position]

        // Set title
        holder.todoTitle.text = todo.title.ifEmpty { "Untitled" }

        // Show/hide pin icon
        holder.pinIcon.visibility = if (todo.isPinned) View.VISIBLE else View.GONE

        // Clear previous checklist items
        holder.checklistContainer.removeAllViews()

        // Show preview of checklist items (max 3)
        val itemsToShow = todo.items.take(maxPreviewItems)
        for (item in itemsToShow) {
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_checklist_preview, holder.checklistContainer, false)

            val checkbox = itemView.findViewById<CheckBox>(R.id.checkboxItem)
            val itemText = itemView.findViewById<TextView>(R.id.itemText)

            checkbox.isChecked = item.isChecked
            itemText.text = item.text

            // Strike through completed items
            if (item.isChecked) {
                itemText.paintFlags = itemText.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                itemText.alpha = 0.5f
            } else {
                itemText.paintFlags = itemText.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                itemText.alpha = 1.0f
            }

            holder.checklistContainer.addView(itemView)
        }

        // Show "more items" indicator if there are more than 3 items
        val remainingCount = todo.items.size - maxPreviewItems
        if (remainingCount > 0) {
            holder.moreItemsText.visibility = View.VISIBLE
            holder.moreItemsText.text = "+$remainingCount more item${if (remainingCount > 1) "s" else ""}"
        } else {
            holder.moreItemsText.visibility = View.GONE
        }

        // Set timestamp
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            todo.timeModified,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.todoTimestamp.text = relativeTime

        // Set click listener
        holder.todoCard.setOnClickListener {
            onItemClick(todo)
        }
    }

    override fun getItemCount(): Int = todoList.size
}