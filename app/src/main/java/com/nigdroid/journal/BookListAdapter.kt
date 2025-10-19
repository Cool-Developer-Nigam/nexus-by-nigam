package com.nigdroid.journal

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class BookListAdapter(val items: MutableList<BookModel>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_ITEM1 = 1
        const val TYPE_ITEM2 = 0
    }

    lateinit var context: Context

    override fun getItemViewType(position: Int): Int {
        return if (position % 2 == 0) {
            TYPE_ITEM1
        } else {
            TYPE_ITEM2
        }
    }

    class ViewHolderItem2(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val picMain: ImageView = itemView.findViewById(R.id.picMain)
        val titleTxt: TextView = itemView.findViewById(R.id.titleTxt)
        val priceTxt: TextView = itemView.findViewById(R.id.priceTxt)
        val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar)
    }

    class ViewHolderItem1(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val picMain: ImageView = itemView.findViewById(R.id.picMain)
        val titleTxt: TextView = itemView.findViewById(R.id.titleTxt)
        val priceTxt: TextView = itemView.findViewById(R.id.priceTxt)
        val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context

        return when (viewType) {
            TYPE_ITEM2 -> {
                val view = LayoutInflater.from(context)
                    .inflate(R.layout.viewholder_item_pic_left, parent, false)
                ViewHolderItem2(view)
            }
            TYPE_ITEM1 -> {
                val view = LayoutInflater.from(context)
                    .inflate(R.layout.viewholder_item_pic_right, parent, false)
                ViewHolderItem1(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        fun bindCommonData(
            titleTxt: String,
            author: String,
            rating: Float,
            picUrl: String
        ) {
            when (holder) {
                is ViewHolderItem1 -> {
                    holder.titleTxt.text = titleTxt
                    holder.priceTxt.text = author
                    holder.ratingBar.rating = rating

                    Glide.with(context)
                        .load(picUrl)
                        .into(holder.picMain)

                    holder.itemView.setOnClickListener {
                        val intent = Intent(context, BookDetailActivity::class.java)
                        intent.putExtra("object", item)
                        context.startActivity(intent)
                    }
                }
                is ViewHolderItem2 -> {
                    holder.titleTxt.text = titleTxt
                    holder.priceTxt.text = author
                    holder.ratingBar.rating = rating

                    Glide.with(context)
                        .load(picUrl)
                        .into(holder.picMain)

                    holder.itemView.setOnClickListener {
                        val intent = Intent(context, BookDetailActivity::class.java)
                        intent.putExtra("object", item)
                        context.startActivity(intent)
                    }
                }
            }
        }

        bindCommonData(item.title, item.author, item.rating.toFloat(), item.picUrl)
    }
}
