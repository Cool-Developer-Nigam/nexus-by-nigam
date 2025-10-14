package com.nigdroid.journal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nigdroid.journal.databinding.ActivityAddTextNoteBinding
import com.nigdroid.journal.databinding.DialogConfirmDeleteBinding
import com.nigdroid.journal.utils.TextFormattingHelper
import com.nigdroid.journal.utils.BackgroundColorHelper

class AddTextNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTextNoteBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private var noteId: String? = null
    private var isPinned = false
    private var isEditMode = false

    // Formatting helpers
    private lateinit var titleFormattingHelper: TextFormattingHelper
    private lateinit var contentFormattingHelper: TextFormattingHelper
    private lateinit var backgroundColorHelper: BackgroundColorHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_text_note)

        firebaseAuth = FirebaseAuth.getInstance()

        noteId = intent.getStringExtra("NOTE_ID")
        isEditMode = noteId != null

        setupToolbar()
        setupFormatting()
        setupFocusListeners()
        setupClickListeners()

        if (isEditMode) {
            loadNoteData()
        }
    }

    private fun setupToolbar() {
        binding.backBtn.setOnClickListener {
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
        // Title formatting helper
        titleFormattingHelper = TextFormattingHelper(
            editText = binding.etTitle,
            boldButton = binding.btnTitleBold,
            italicButton = binding.btnTitleItalic,
            underlineButton = binding.btnTitleUnderline,
            textColorButton = binding.btnTitleTextColor
        )

        // Content formatting helper
        contentFormattingHelper = TextFormattingHelper(
            editText = binding.etContent,
            boldButton = binding.btnContentBold,
            italicButton = binding.btnContentItalic,
            underlineButton = binding.btnContentUnderline,
            textColorButton = binding.btnContentTextColor
        )

        // Background color helper
        backgroundColorHelper = BackgroundColorHelper(
            targetView = binding.noteContainer,
            colorButton = binding.btnTitleBackgroundColor
        )

        // Also link content background button
        binding.btnContentBackgroundColor.setOnClickListener {
            backgroundColorHelper.showColorPicker()
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
    }

    private fun showTitleFormattingToolbar() {
        binding.titleFormattingToolbar.visibility = View.VISIBLE
        binding.contentFormattingToolbar.visibility = View.GONE
        titleFormattingHelper.updateButtonStates()
    }

    private fun hideTitleFormattingToolbar() {
        binding.titleFormattingToolbar.visibility = View.GONE
    }

    private fun showContentFormattingToolbar() {
        binding.contentFormattingToolbar.visibility = View.VISIBLE
        binding.titleFormattingToolbar.visibility = View.GONE
        contentFormattingHelper.updateButtonStates()
    }

    private fun hideContentFormattingToolbar() {
        binding.contentFormattingToolbar.visibility = View.GONE
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
                            binding.etTitle.setText(
                                android.text.Html.fromHtml(
                                    it.title,
                                    android.text.Html.FROM_HTML_MODE_COMPACT
                                )
                            )
                            binding.etContent.setText(
                                android.text.Html.fromHtml(
                                    it.content,
                                    android.text.Html.FROM_HTML_MODE_COMPACT
                                )
                            )

                            backgroundColorHelper.setBackgroundColor(it.backgroundColor)
                            isPinned = it.isPinned
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
        val title = android.text.Html.toHtml(
            binding.etTitle.text as android.text.Spanned,
            android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE
        )
        val content = android.text.Html.toHtml(
            binding.etContent.text as android.text.Spanned,
            android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE
        )

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
            textColor = contentFormattingHelper.textColor,
            backgroundColor = backgroundColorHelper.backgroundColor,
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
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)
        val dialogBinding = DialogConfirmDeleteBinding.inflate(LayoutInflater.from(this))

        dialogBinding.btnDelete.setOnClickListener {
            deleteNote()
            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
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