package com.nigdroid.journal

import android.os.Build
import android.text.Html
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.util.Date

/**
 * Custom BindingAdapters for handling HTML content and images in data binding
 */
object BindingAdapters {

    /**
     * Binding adapter to render HTML text in TextView
     * Usage in XML: app:htmlText="@{journal.title}"
     */
    @JvmStatic
    @BindingAdapter("htmlText")
    fun setHtmlText(view: TextView, html: String?) {
        if (html.isNullOrEmpty()) {
            view.text = ""
            return
        }

        view.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
    }

    /**
     * Binding adapter to load images from URL with proper sizing
     * Usage in XML: app:imageUrl="@{journal.imageUrl}"
     */
    @JvmStatic
    @BindingAdapter("imageUrl")
    fun loadImage(view: ImageView, url: String?) {
        if (!url.isNullOrEmpty()) {
            Glide.with(view.context)
                .load(url)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.color.darker_gray)
                .error(android.R.drawable.ic_menu_report_image)
                .into(view)
        } else {
            view.setImageDrawable(null)
        }
    }

    /**
     * Binding adapter to format timestamp as relative time (e.g., "2 days ago")
     * Usage in XML: app:timeAgo="@{journal.timeAdded}"
     */
    @JvmStatic
    @BindingAdapter("timeAgo")
    fun setTimeAgo(view: TextView, timestamp: Any?) {
        if (timestamp == null) {
            view.text = ""
            return
        }

        val timeInMillis = when (timestamp) {
            is Long -> timestamp
            is Date -> timestamp.time
            is String -> {
                // Try to parse string as long
                timestamp.toLongOrNull() ?: run {
                    view.text = timestamp
                    return
                }
            }
            else -> {
                view.text = timestamp.toString()
                return
            }
        }

        val now = System.currentTimeMillis()
        view.text = DateUtils.getRelativeTimeSpanString(
            timeInMillis,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
    }

    /**
     * Binding adapter for RecyclerView items with fixed height
     * Usage in XML: app:imageUrlFixed="@{journal.imageUrl}"
     */
    @JvmStatic
    @BindingAdapter("imageUrlFixed")
    fun loadImageFixed(view: ImageView, url: String?) {
        if (!url.isNullOrEmpty()) {
            view.visibility = View.VISIBLE

            // Make sure parent CardView is visible
            var parent = view.parent
            while (parent != null) {
                if (parent is androidx.cardview.widget.CardView) {
                    parent.visibility = View.VISIBLE
                    break
                }
                parent = parent.parent
            }

            Glide.with(view.context)
                .load(url)
                .centerCrop()
                .override(800, 400) // Fixed dimensions for list items
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.color.darker_gray)
                .error(android.R.drawable.ic_menu_report_image)
                .into(view)
        } else {
            // Hide the image CardView if there's no image
            var parent = view.parent
            while (parent != null) {
                if (parent is androidx.cardview.widget.CardView) {
                    parent.visibility = View.GONE
                    break
                }
                parent = parent.parent
            }
        }
    }
}