package com.nigdroid.journal

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView

class ChecklistEditAdapter(
    private val context: Context,
    private val items: MutableList<ChecklistItem>,
    private val onItemChanged: (Int, ChecklistItem) -> Unit,
    private val onDeleteItem: (Int) -> Unit
) : RecyclerView.Adapter<ChecklistEditAdapter.ChecklistViewHolder>() {

    inner class ChecklistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkbox: CheckBox = itemView.findViewById(R.id.checkboxItem)
        val editText: EditText = itemView.findViewById(R.id.etItemText)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btnDeleteItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChecklistViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_checklist_edit, parent, false)
        return ChecklistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChecklistViewHolder, position: Int) {
        val item = items[position]

        // Remove previous listeners to avoid triggering them during bind
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.editText.removeTextChangedListener(holder.editText.tag as? TextWatcher)

        // Set current values
        holder.checkbox.isChecked = item.isChecked
        holder.editText.setText(item.text)

        // Show delete button only if text is not empty or item is focused
        holder.deleteButton.visibility = if (item.text.isNotEmpty()) View.VISIBLE else View.GONE

        // Checkbox listener
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            items[position].isChecked = isChecked

            // Strike through text when checked
            if (isChecked) {
                holder.editText.paintFlags = holder.editText.paintFlags or
                        android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                holder.editText.alpha = 0.5f
            } else {
                holder.editText.paintFlags = holder.editText.paintFlags and
                        android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.editText.alpha = 1.0f
            }

            onItemChanged(position, items[position])
        }

        // Text change listener
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    items[currentPos].text = s.toString()

                    // Show/hide delete button based on text
                    holder.deleteButton.visibility = if (s.toString().isNotEmpty())
                        View.VISIBLE else View.GONE

                    onItemChanged(currentPos, items[currentPos])
                }
            }
        }

        holder.editText.tag = textWatcher
        holder.editText.addTextChangedListener(textWatcher)

        // Delete button listener
        holder.deleteButton.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onDeleteItem(currentPos)
            }
        }

        // Apply strike through if already checked
        if (item.isChecked) {
            holder.editText.paintFlags = holder.editText.paintFlags or
                    android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            holder.editText.alpha = 0.5f
        } else {
            holder.editText.paintFlags = holder.editText.paintFlags and
                    android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.editText.alpha = 1.0f
        }
    }

    override fun getItemCount(): Int = items.size
}