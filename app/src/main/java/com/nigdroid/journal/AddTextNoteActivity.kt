package com.nigdroid.journal

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    private var currentTextColor = "#000000"
    private var currentBackgroundColor = "#FFFFFF"

    private var isBold = false
    private var isItalic = false
    private var isUnderline = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_text_note)

        firebaseAuth = FirebaseAuth.getInstance()

        noteId = intent.getStringExtra("NOTE_ID")
        isEditMode = noteId != null

        setupToolbar()
        setupFormatting()
        setupClickListeners()

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

    private fun setupFormatting() {
        // Bold button
        binding.btnBold.setOnClickListener {
            isBold = !isBold
            updateFormattingButtonState(binding.btnBold, isBold)
            applyFormatting()
        }

        // Italic button
        binding.btnItalic.setOnClickListener {
            isItalic = !isItalic
            updateFormattingButtonState(binding.btnItalic, isItalic)
            applyFormatting()
        }

        // Underline button
        binding.btnUnderline.setOnClickListener {
            isUnderline = !isUnderline
            updateFormattingButtonState(binding.btnUnderline, isUnderline)
            applyFormatting()
        }

        // Text color picker
        binding.btnTextColor.setOnClickListener {
            showColorPicker(true)
        }

        // Background color picker
        binding.btnBackgroundColor.setOnClickListener {
            showColorPicker(false)
        }
    }

    private fun updateFormattingButtonState(button: View, isActive: Boolean) {
        if (isActive) {
            button.setBackgroundResource(R.drawable.formatting_button_active)
        } else {
            button.setBackgroundResource(R.drawable.formatting_button_normal)
        }
    }

    private fun applyFormatting() {
        val start = binding.etContent.selectionStart
        val end = binding.etContent.selectionEnd

        if (start >= 0 && end > start) {
            val spannable = SpannableString(binding.etContent.text)

            // Remove existing spans in selection
            val spans = spannable.getSpans(start, end, Any::class.java)
            for (span in spans) {
                spannable.removeSpan(span)
            }

            // Apply bold
            if (isBold) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Apply italic
            if (isItalic) {
                spannable.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Apply underline
            if (isUnderline) {
                spannable.setSpan(
                    UnderlineSpan(),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Apply text color
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor(currentTextColor)),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            binding.etContent.text = spannable as Editable?
            binding.etContent.setSelection(end)
        }
    }

    private fun showColorPicker(isTextColor: Boolean) {
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
            "#808080" to "Gray"
        )

        val colorNames = colors.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(if (isTextColor) "Select Text Color" else "Select Background Color")
            .setItems(colorNames) { _, which ->
                val selectedColor = colors[which].first
                if (isTextColor) {
                    currentTextColor = selectedColor
                    binding.btnTextColor.setColorFilter(Color.parseColor(selectedColor))
                } else {
                    currentBackgroundColor = selectedColor
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
                            binding.etTitle.setText(it.title)
                            binding.etContent.setText(android.text.Html.fromHtml(it.content, android.text.Html.FROM_HTML_MODE_COMPACT))
                            currentTextColor = it.textColor
                            currentBackgroundColor = it.backgroundColor
                            isPinned = it.isPinned

                            binding.btnTextColor.setColorFilter(Color.parseColor(currentTextColor))
                            binding.noteContainer.setBackgroundColor(Color.parseColor(currentBackgroundColor))
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
        val title = binding.etTitle.text.toString().trim()
        val content = android.text.Html.toHtml(binding.etContent.text as android.text.Spanned, android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)

        if (title.isEmpty() && content.isEmpty()) {
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
            textColor = currentTextColor,
            backgroundColor = currentBackgroundColor,
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