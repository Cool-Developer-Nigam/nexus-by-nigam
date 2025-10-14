package com.nigdroid.journal.utils

import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import com.nigdroid.journal.R

/**
 * Helper class for text formatting functionality
 * Handles bold, italic, underline, and color formatting
 */
class TextFormattingHelper(
    private val editText: EditText,
    private val boldButton: ImageButton? = null,
    private val italicButton: ImageButton? = null,
    private val underlineButton: ImageButton? = null,
    private val textColorButton: ImageButton? = null
) {

    // Formatting states
    var textColor = "#000000"
        private set
    var isBold = false
        private set
    var isItalic = false
        private set
    var isUnderline = false
        private set

    private var isApplyingFormatting = false

    init {
        setupButtons()
        setupTextWatcher()
    }

    private fun setupButtons() {
        boldButton?.setOnClickListener {
            isBold = !isBold
            updateButtonState(boldButton, isBold)
            applyFormattingToSelection()
        }

        italicButton?.setOnClickListener {
            isItalic = !isItalic
            updateButtonState(italicButton, isItalic)
            applyFormattingToSelection()
        }

        underlineButton?.setOnClickListener {
            isUnderline = !isUnderline
            updateButtonState(underlineButton, isUnderline)
            applyFormattingToSelection()
        }

        textColorButton?.setOnClickListener {
            showColorPicker()
        }
    }

    private fun setupTextWatcher() {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0 && !isApplyingFormatting && editText.hasFocus()) {
                    applyFormattingToNewText(start, start + count)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyFormattingToNewText(start: Int, end: Int) {
        if (start >= end) return

        isApplyingFormatting = true
        val spannable = editText.text ?: return

        var style = Typeface.NORMAL
        if (isBold && isItalic) {
            style = Typeface.BOLD_ITALIC
        } else if (isBold) {
            style = Typeface.BOLD
        } else if (isItalic) {
            style = Typeface.ITALIC
        }

        if (style != Typeface.NORMAL) {
            spannable.setSpan(
                StyleSpan(style),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (isUnderline) {
            spannable.setSpan(
                UnderlineSpan(),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (textColor != "#000000") {
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor(textColor)),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        isApplyingFormatting = false
    }

    private fun applyFormattingToSelection() {
        val start = editText.selectionStart
        val end = editText.selectionEnd

        if (start >= 0 && end > start) {
            val spannable = editText.text ?: return

            // Remove existing spans
            val styleSpans = spannable.getSpans(start, end, StyleSpan::class.java)
            for (span in styleSpans) {
                spannable.removeSpan(span)
            }

            val underlineSpans = spannable.getSpans(start, end, UnderlineSpan::class.java)
            for (span in underlineSpans) {
                spannable.removeSpan(span)
            }

            var style = Typeface.NORMAL
            if (isBold && isItalic) {
                style = Typeface.BOLD_ITALIC
            } else if (isBold) {
                style = Typeface.BOLD
            } else if (isItalic) {
                style = Typeface.ITALIC
            }

            if (style != Typeface.NORMAL) {
                spannable.setSpan(
                    StyleSpan(style),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            if (isUnderline) {
                spannable.setSpan(
                    UnderlineSpan(),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            if (textColor != "#000000") {
                val colorSpans = spannable.getSpans(start, end, ForegroundColorSpan::class.java)
                for (span in colorSpans) {
                    spannable.removeSpan(span)
                }

                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor(textColor)),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            editText.setSelection(end)
        }
    }

    private fun showColorPicker() {
        val colors = arrayOf(
            "#000000" to "Black",
            "#FF0000" to "Red",
            "#00FF00" to "Green",
            "#0000FF" to "Blue",
            "#FFFF00" to "Yellow",
            "#FF00FF" to "Magenta",
            "#00FFFF" to "Cyan",
            "#FFA500" to "Orange",
            "#800080" to "Purple",
            "#FFC0CB" to "Pink",
            "#A52A2A" to "Brown",
            "#808080" to "Gray",
            "#FFFFFF" to "White"
        )

        val colorNames = colors.map { it.second }.toTypedArray()

        AlertDialog.Builder(editText.context)
            .setTitle("Select Text Color")
            .setItems(colorNames) { _, which ->
                textColor = colors[which].first
                textColorButton?.setColorFilter(Color.parseColor(textColor))
                applyFormattingToSelection()
            }
            .show()
    }

    private fun updateButtonState(button: ImageButton, isActive: Boolean) {
        if (isActive) {
            button.setBackgroundResource(R.drawable.formatting_button_active)
        } else {
            button.setBackgroundResource(R.drawable.formatting_button_normal)
        }
    }

    fun updateButtonStates() {
        boldButton?.let { updateButtonState(it, isBold) }
        italicButton?.let { updateButtonState(it, isItalic) }
        underlineButton?.let { updateButtonState(it, isUnderline) }
        textColorButton?.setColorFilter(Color.parseColor(textColor))
    }
}