package com.nigdroid.journal

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class BookCategoryAdapter(val items: MutableList<CategoryModel>) :
    RecyclerView.Adapter<BookCategoryAdapter.ViewHolder>() {

    private lateinit var context: Context
    private var selectedPosition = -1
    private var lastSelectedPosition = -1

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleCat: TextView = itemView.findViewById(R.id.titleCat)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context)
            .inflate(R.layout.view_holder_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleCat.text = item.title

        holder.itemView.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                lastSelectedPosition = selectedPosition
                selectedPosition = currentPosition
                notifyItemChanged(lastSelectedPosition)
                notifyItemChanged(selectedPosition)

                Handler(Looper.getMainLooper()).postDelayed({
                    val latestPosition = holder.adapterPosition
                    if (latestPosition != RecyclerView.NO_POSITION && latestPosition < items.size) {
                        val latestItem = items[latestPosition]
                        val intent = Intent(context, BookListActivity::class.java).apply {
                            putExtra("id", latestItem.id.toString())
                            putExtra("title", latestItem.title)
                        }
                        ContextCompat.startActivity(context, intent, null)
                    } else {
                        Log.w("BookCategoryAdapter", "Item position invalid")
                    }
                }, 200)
            }
        }

        if (selectedPosition == position) {
            holder.titleCat.setBackgroundResource(R.drawable.costum_bg)
            holder.titleCat.setTextColor(context.resources.getColor(R.color.white))
        } else {
            holder.titleCat.setBackgroundResource(R.drawable.white_bg)
            holder.titleCat.setTextColor(context.resources.getColor(R.color.purple_200))
        }
    }

    override fun getItemCount(): Int = items.size
}