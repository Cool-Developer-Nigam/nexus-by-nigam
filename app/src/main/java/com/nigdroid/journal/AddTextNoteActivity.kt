package com.nigdroid.journal

import android.os.Bundle
import android.util.Log
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
    private var originalTimeAdded: Long = 0L

    // Formatting helpers
    private lateinit var titleFormattingHelper: TextFormattingHelper
    private lateinit var contentFormattingHelper: TextFormattingHelper
    private lateinit var backgroundColorHelper: BackgroundColorHelper

    companion object {
        private const val TAG = "AddTextNoteActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_text_note)

        firebaseAuth = FirebaseAuth.getInstance()

        noteId = intent.getStringExtra("NOTE_ID")
        isEditMode = noteId != null

        // Initialize pin state from intent (for edit mode)
        isPinned = intent.getBooleanExtra("IS_PINNED", false)
        originalTimeAdded = intent.getLongExtra("TIME_ADDED", 0L)

        Log.d(TAG, "onCreate - isEditMode: $isEditMode, noteId: $noteId, isPinned: $isPinned")

        setupToolbar()
        setupFormatting()
        setupFocusListeners()
        setupClickListeners()

        if (isEditMode) {
            loadNoteData()
        } else {
            // New note - start with unpinned
            isPinned = false
            updatePinIcon()
            Log.d(TAG, "New note created - isPinned initialized to false")
        }
    }

    private fun setupToolbar() {
        binding.backBtn.setOnClickListener {
            finish()
        }

        binding.btnPin.setOnClickListener {
            isPinned = !isPinned
            updatePinIcon()

            val message = if (isPinned) "Note will be pinned when saved" else "Note will be unpinned when saved"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            Log.d(TAG, "Pin button clicked - isPinned is now: $isPinned")
        }

        updatePinIcon()
    }

    private fun updatePinIcon() {
        if (isPinned) {
            binding.btnPin.setImageResource(R.drawable.ic_pin)
            binding.btnPin.alpha = 1.0f
            Log.d(TAG, "Pin icon updated - showing FILLED pin")
        } else {
            binding.btnPin.setImageResource(R.drawable.ic_pin_outline)
            binding.btnPin.alpha = 0.7f
            Log.d(TAG, "Pin icon updated - showing OUTLINE pin")
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
            binding.progressBar.visibility = View.VISIBLE
            Log.d(TAG, "Loading note data for ID: $id")

            db.collection("TextNotes").document(id).get()
                .addOnSuccessListener { document ->
                    binding.progressBar.visibility = View.GONE
                    if (document.exists()) {
                        val note = document.toObject(TextNote::class.java)
                        note?.let {
                            Log.d(TAG, "Note loaded - isPinned from Firestore: ${it.isPinned}")

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

                            // Load pin state from Firestore
                            isPinned = it.isPinned
                            originalTimeAdded = it.timeAdded
                            updatePinIcon()

                            Log.d(TAG, "Note loaded successfully - isPinned: $isPinned, timeAdded: $originalTimeAdded")
                        }
                    } else {
                        Toast.makeText(this, "Note not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Log.e(TAG, "Error loading note", e)
                    Toast.makeText(this, "Error loading note: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveNote() {
        Log.d(TAG, "saveNote() called - isPinned: $isPinned")

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

        val user = firebaseAuth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val username = sharedPref.getString("username", "Anonymous") ?: "Anonymous"

        binding.progressBar.visibility = View.VISIBLE

        val currentTime = System.currentTimeMillis()

        if (isEditMode && noteId != null) {
            // UPDATE EXISTING NOTE
            val timeAdded = if (originalTimeAdded > 0) originalTimeAdded else currentTime

            val textNote = TextNote(
                id = noteId!!,
                title = title,
                content = content,
                textColor = contentFormattingHelper.textColor,
                backgroundColor = backgroundColorHelper.backgroundColor,
                userId = user.uid,
                username = username,
                timeAdded = timeAdded,
                timeModified = currentTime,
                isPinned = isPinned
            )

            Log.d(TAG, "Updating note - isPinned: ${textNote.isPinned}, id: ${textNote.id}")

            db.collection("TextNotes").document(noteId!!)
                .set(textNote)
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    val message = "Note updated" + if (isPinned) " (Pinned)" else " (Unpinned)"
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Note updated successfully in Firestore - isPinned: $isPinned")
                    finish()
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Log.e(TAG, "Error updating note", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // CREATE NEW NOTE
            val textNote = TextNote(
                id = "", // Will be updated after document creation
                title = title,
                content = content,
                textColor = contentFormattingHelper.textColor,
                backgroundColor = backgroundColorHelper.backgroundColor,
                userId = user.uid,
                username = username,
                timeAdded = currentTime,
                timeModified = currentTime,
                isPinned = isPinned
            )

            Log.d(TAG, "Creating new note - isPinned: ${textNote.isPinned}")

            db.collection("TextNotes")
                .add(textNote)
                .addOnSuccessListener { documentReference ->
                    val generatedId = documentReference.id
                    Log.d(TAG, "Note created with ID: $generatedId, now updating ID field")

                    documentReference.update("id", generatedId)
                        .addOnSuccessListener {
                            binding.progressBar.visibility = View.GONE
                            val message = "Note saved" + if (isPinned) " (Pinned)" else ""
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Note saved successfully - isPinned: $isPinned")
                            finish()
                        }
                        .addOnFailureListener { e ->
                            binding.progressBar.visibility = View.GONE
                            Log.e(TAG, "Error updating ID", e)
                            Toast.makeText(this, "Error updating ID: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Log.e(TAG, "Error creating note", e)
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
            binding.progressBar.visibility = View.VISIBLE
            Log.d(TAG, "Deleting note ID: $id")

            db.collection("TextNotes").document(id)
                .delete()
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Note deleted successfully")
                    finish()
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Log.e(TAG, "Error deleting note", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}