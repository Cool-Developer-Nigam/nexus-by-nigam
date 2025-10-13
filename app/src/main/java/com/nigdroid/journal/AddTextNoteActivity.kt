package com.nigdroid.journal

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nigdroid.journal.databinding.ActivityAddTextNoteBinding

class AddTextNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTextNoteBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private var noteId: String? = null
    private var isPinned = false
    private var isEditMode = false

    private var sharedBackgroundColor = "#FFFFFF"

    // Title formatting states
    private var titleTextColor = "#000000"
    private var isTitleBold = false
    private var isTitleItalic = false
    private var isTitleUnderline = false

    // Content formatting states
    private var contentTextColor = "#000000"
    private var isContentBold = false
    private var isContentItalic = false
    private var isContentUnderline = false

    private var isApplyingFormatting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_text_note)

        firebaseAuth = FirebaseAuth.getInstance()

        noteId = intent.getStringExtra("NOTE_ID")
        isEditMode = noteId != null

        setupToolbar()
        setupFormatting()
        setupClickListeners()
        setupFocusListeners()

        if (isEditMode) {
            loadNoteData()
        }
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnPin.setOnClickListener {
            isPinned = !isPinned
            updatePinIcon()
        }

        isPinned = intent.getBooleanExtra("IS_PINNED", false)
        updatePinIcon()
    }

    private fun updatePinIcon() {
        if (isPinned) {
            binding.btnPin.setImageResource(R.drawable.ic_pin)
        } else {
            binding.btnPin.setImageResource(R.drawable.ic_pin_outline)
        }
    }

    private fun setupFocusListeners() {
        binding.etTitle.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showTitleFormattingToolbar()
            } else {
                hideTitleFormattingToolbar()
            }
        }

        binding.etContent.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showContentFormattingToolbar()
            } else {
                hideContentFormattingToolbar()
            }
        }

        // Set up text watchers
        setupTextWatcher(binding.etTitle, true)
        setupTextWatcher(binding.etContent, false)
    }

    private fun showTitleFormattingToolbar() {
        binding.titleFormattingToolbar.visibility = View.VISIBLE
        binding.contentFormattingToolbar.visibility = View.GONE
        updateTitleFormattingButtons()
    }

    private fun hideTitleFormattingToolbar() {
        binding.titleFormattingToolbar.visibility = View.GONE
    }

    private fun showContentFormattingToolbar() {
        binding.contentFormattingToolbar.visibility = View.VISIBLE
        binding.titleFormattingToolbar.visibility = View.GONE
        updateContentFormattingButtons()
    }

    private fun hideContentFormattingToolbar() {
        binding.contentFormattingToolbar.visibility = View.GONE
    }

    private fun updateTitleFormattingButtons() {
        updateFormattingButtonState(binding.btnTitleBold, isTitleBold)
        updateFormattingButtonState(binding.btnTitleItalic, isTitleItalic)
        updateFormattingButtonState(binding.btnTitleUnderline, isTitleUnderline)
        binding.btnTitleTextColor.setColorFilter(Color.parseColor(titleTextColor))
    }

    private fun updateContentFormattingButtons() {
        updateFormattingButtonState(binding.btnContentBold, isContentBold)
        updateFormattingButtonState(binding.btnContentItalic, isContentItalic)
        updateFormattingButtonState(binding.btnContentUnderline, isContentUnderline)
        binding.btnContentTextColor.setColorFilter(Color.parseColor(contentTextColor))
    }

    private fun setupFormatting() {
        // Title formatting buttons
        binding.btnTitleBold.setOnClickListener {
            isTitleBold = !isTitleBold
            updateFormattingButtonState(binding.btnTitleBold, isTitleBold)
            applyFormattingToSelection(binding.etTitle, true)
        }

        binding.btnTitleItalic.setOnClickListener {
            isTitleItalic = !isTitleItalic
            updateFormattingButtonState(binding.btnTitleItalic, isTitleItalic)
            applyFormattingToSelection(binding.etTitle, true)
        }

        binding.btnTitleUnderline.setOnClickListener {
            isTitleUnderline = !isTitleUnderline
            updateFormattingButtonState(binding.btnTitleUnderline, isTitleUnderline)
            applyFormattingToSelection(binding.etTitle, true)
        }

        binding.btnTitleTextColor.setOnClickListener {
            showColorPicker(true, true)
        }

        binding.btnTitleBackgroundColor.setOnClickListener {
            showColorPicker(false, true)
        }

        // Content formatting buttons
        binding.btnContentBold.setOnClickListener {
            isContentBold = !isContentBold
            updateFormattingButtonState(binding.btnContentBold, isContentBold)
            applyFormattingToSelection(binding.etContent, false)
        }

        binding.btnContentItalic.setOnClickListener {
            isContentItalic = !isContentItalic
            updateFormattingButtonState(binding.btnContentItalic, isContentItalic)
            applyFormattingToSelection(binding.etContent, false)
        }

        binding.btnContentUnderline.setOnClickListener {
            isContentUnderline = !isContentUnderline
            updateFormattingButtonState(binding.btnContentUnderline, isContentUnderline)
            applyFormattingToSelection(binding.etContent, false)
        }

        binding.btnContentTextColor.setOnClickListener {
            showColorPicker(true, false)
        }

        binding.btnContentBackgroundColor.setOnClickListener {
            showColorPicker(false, false)
        }
    }

    private fun updateFormattingButtonState(button: View, isActive: Boolean) {
        if (isActive) {
            button.setBackgroundResource(R.drawable.formatting_button_active)
        } else {
            button.setBackgroundResource(R.drawable.formatting_button_normal)
        }
    }

    private fun setupTextWatcher(editText: EditText, isTitle: Boolean) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0 && !isApplyingFormatting && editText.hasFocus()) {
                    applyFormattingToNewText(editText, start, start + count, isTitle)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyFormattingToNewText(editText: EditText, start: Int, end: Int, isTitle: Boolean) {
        if (start >= end) return

        isApplyingFormatting = true
        val spannable = editText.text ?: return

        val isBold = if (isTitle) isTitleBold else isContentBold
        val isItalic = if (isTitle) isTitleItalic else isContentItalic
        val isUnderline = if (isTitle) isTitleUnderline else isContentUnderline
        val textColor = if (isTitle) titleTextColor else contentTextColor

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

    private fun applyFormattingToSelection(editText: EditText, isTitle: Boolean) {
        val start = editText.selectionStart
        val end = editText.selectionEnd

        val isBold = if (isTitle) isTitleBold else isContentBold
        val isItalic = if (isTitle) isTitleItalic else isContentItalic
        val isUnderline = if (isTitle) isTitleUnderline else isContentUnderline
        val textColor = if (isTitle) titleTextColor else contentTextColor

        if (start >= 0 && end > start) {
            val spannable = editText.text ?: return

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

    private fun showColorPicker(isTextColor: Boolean, isForTitle: Boolean) {
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

        val title = if (isTextColor) {
            if (isForTitle) "Select Title Text Color" else "Select Content Text Color"
        } else {
            "Select Background Color"
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(colorNames) { _, which ->
                val selectedColor = colors[which].first
                if (isTextColor) {
                    if (isForTitle) {
                        titleTextColor = selectedColor
                        binding.btnTitleTextColor.setColorFilter(Color.parseColor(selectedColor))
                        applyFormattingToSelection(binding.etTitle, true)
                    } else {
                        contentTextColor = selectedColor
                        binding.btnContentTextColor.setColorFilter(Color.parseColor(selectedColor))
                        applyFormattingToSelection(binding.etContent, false)
                    }
                } else {
                    sharedBackgroundColor = selectedColor
                    binding.noteContainer.setBackgroundColor(Color.parseColor(selectedColor))
                }
            }
            .show()
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            saveNote()
        }

        binding.btnDelete.setOnClickListener {
            if (isEditMode) {
                showDeleteConfirmationDialog()
            } else {
                finish()
            }
        }
    }

    private fun loadNoteData() {
        noteId?.let { id ->
            db.collection("TextNotes").document(id).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val note = document.toObject(TextNote::class.java)
                        note?.let {
                            binding.etTitle.setText(android.text.Html.fromHtml(it.title, android.text.Html.FROM_HTML_MODE_COMPACT))
                            binding.etContent.setText(android.text.Html.fromHtml(it.content, android.text.Html.FROM_HTML_MODE_COMPACT))

                            titleTextColor = it.textColor
                            contentTextColor = it.textColor
                            sharedBackgroundColor = it.backgroundColor
                            isPinned = it.isPinned

                            binding.noteContainer.setBackgroundColor(Color.parseColor(sharedBackgroundColor))
                            updatePinIcon()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading note: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveNote() {
        val title = android.text.Html.toHtml(binding.etTitle.text as android.text.Spanned, android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        val content = android.text.Html.toHtml(binding.etContent.text as android.text.Spanned, android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)

        if (title.trim().isEmpty() && content.trim().isEmpty()) {
            Toast.makeText(this, "Please add a title or content", Toast.LENGTH_SHORT).show()
            return
        }

        val user = firebaseAuth.currentUser ?: return
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val username = sharedPref.getString("username", "Anonymous") ?: "Anonymous"

        binding.progressBar.visibility = View.VISIBLE

        val currentTime = System.currentTimeMillis()

        val textNote = TextNote(
            id = noteId ?: "",
            title = title,
            content = content,
            textColor = contentTextColor,
            backgroundColor = sharedBackgroundColor,
            userId = user.uid,
            username = username,
            timeAdded = if (isEditMode) intent.getLongExtra("TIME_ADDED", currentTime) else currentTime,
            timeModified = currentTime,
            isPinned = isPinned
        )

        if (isEditMode && noteId != null) {
            db.collection("TextNotes").document(noteId!!)
                .set(textNote)
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            db.collection("TextNotes")
                .add(textNote)
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                deleteNote()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteNote() {
        noteId?.let { id ->
            db.collection("TextNotes").document(id)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}