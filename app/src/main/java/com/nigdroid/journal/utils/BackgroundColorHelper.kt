package com.nigdroid.journal.utils

import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog

/**
 * Helper class for background color selection
 */
class BackgroundColorHelper(
    private val targetView: View,
    private val colorButton: ImageButton? = null
) {

    var backgroundColor = "#FFFFFF"
        private set

    init {
        colorButton?.setOnClickListener {
            showColorPicker()
        }
    }

    fun showColorPicker() {
        val colors = arrayOf(
            "#FFFFFF" to "White",
            "#FFFACD" to "Lemon Chiffon",
            "#FFE4E1" to "Misty Rose",
            "#E0F8E0" to "Light Green",
            "#E0F0FF" to "Light Blue",
            "#F0E0FF" to "Light Purple",
            "#FFE0D0" to "Light Peach",
            "#FFF0F5" to "Lavender Blush",
            "#F0FFF0" to "Honeydew",
            "#FFFAF0" to "Floral White",
            "#F5F5DC" to "Beige",
            "#FFB6C1" to "Light Pink",
            "#ADD8E6" to "Light Sky Blue"
        )

        val colorNames = colors.map { it.second }.toTypedArray()

        AlertDialog.Builder(targetView.context)
            .setTitle("Select Background Color")
            .setItems(colorNames) { _, which ->
                backgroundColor = colors[which].first
                applyBackgroundColor()
            }
            .show()
    }

    private fun applyBackgroundColor() {
        targetView.setBackgroundColor(Color.parseColor(backgroundColor))
    }

    fun setBackgroundColor(color: String) {
        backgroundColor = color
        applyBackgroundColor()
    }
}