package com.nigdroid.journal

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.nigdroid.journal.databinding.ActivityEditJournalBinding
import com.nigdroid.journal.databinding.DialogConfirmImageBinding
import com.nigdroid.journal.utils.TextFormattingHelper
import com.nigdroid.journal.utils.BackgroundColorHelper

class EditJournalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditJournalBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var storageReference: StorageReference
    private val auth = FirebaseAuth.getInstance()

    private var imageUri: Uri? = null
    private var currentImageUrl: String = ""
    private lateinit var currentJournal: Journal
    private var imageChanged = false
    private var isPinned = false

    // Formatting helpers
    private var titleFormattingHelper: TextFormattingHelper? = null
    private var thoughtsFormattingHelper: TextFormattingHelper? = null
    private var backgroundColorHelper: BackgroundColorHelper? = null

    companion object {
        private const val TAG = "EditJournalActivity"
        private const val GALLERY_REQUEST_CODE = 1
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_TITLE = "title"
        private const val FIELD_TIME_ADDED = "timeAdded"
        private const val FIELD_THOUGHTS = "thoughts"
        private const val FIELD_IMAGE_URL = "imageUrl"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_TEXT_COLOR = "textColor"
        private const val FIELD_BACKGROUND_COLOR = "backgroundColor"
        private const val FIELD_IS_PINNED = "isPinned"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window full screen without transparency for better UX
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        binding = DataBindingUtil.setContentView(this, R.layout.activity_edit_journal)

        setupThoughtsEditTextScrolling()
        storageReference = FirebaseStorage.getInstance().reference

        currentJournal = Journal(
            intent.getStringExtra(FIELD_TITLE) ?: "",
            intent.getStringExtra(FIELD_THOUGHTS) ?: "",
            intent.getStringExtra(FIELD_IMAGE_URL) ?: "",
            intent.getStringExtra(FIELD_USER_ID) ?: "",
            intent.getStringExtra(FIELD_TIME_ADDED) ?: "",
            intent.getStringExtra(FIELD_USERNAME) ?: "",
            intent.getStringExtra(FIELD_TEXT_COLOR) ?: "#000000",
            intent.getStringExtra(FIELD_BACKGROUND_COLOR) ?: "#FFFFFF",
            intent.getBooleanExtra(FIELD_IS_PINNED, false)
        )

        currentImageUrl = currentJournal.imageUrl
        isPinned = currentJournal.isPinned

        setupFormatting()
        setupViews()
        setupClickListeners()
    }

    private fun setupFormatting() {
        // Title formatting
        binding.titleFormattingToolbar?.let { toolbar ->
            titleFormattingHelper = TextFormattingHelper(
                editText = binding.editTitleEt,
                boldButton = toolbar.findViewById(R.id.btnTitleBold),
                italicButton = toolbar.findViewById(R.id.btnTitleItalic),
                underlineButton = toolbar.findViewById(R.id.btnTitleUnderline),
                textColorButton = toolbar.findViewById(R.id.btnTitleTextColor)
            )
        }

        // Thoughts formatting
        binding.thoughtsFormattingToolbar?.let { toolbar ->
            thoughtsFormattingHelper = TextFormattingHelper(
                editText = binding.etThoughts,
                boldButton = toolbar.findViewById(R.id.btnThoughtsBold),
                italicButton = toolbar.findViewById(R.id.btnThoughtsItalic),
                underlineButton = toolbar.findViewById(R.id.btnThoughtsUnderline),
                textColorButton = toolbar.findViewById(R.id.btnThoughtsTextColor)
            )
        }

        // Background color
        binding.editCard?.let { card ->
            binding.btnBackgroundColor?.let { button ->
                backgroundColorHelper = BackgroundColorHelper(
                    targetView = card,
                    colorButton = button
                )
            }
        }

        // Focus listeners
        binding.editTitleEt.setOnFocusChangeListener { _, hasFocus ->
            binding.titleFormattingToolbar?.visibility = if (hasFocus) View.VISIBLE else View.GONE
            binding.thoughtsFormattingToolbar?.visibility = View.GONE
        }

        binding.etThoughts.setOnFocusChangeListener { _, hasFocus ->
            binding.thoughtsFormattingToolbar?.visibility = if (hasFocus) View.VISIBLE else View.GONE
            binding.titleFormattingToolbar?.visibility = View.GONE
        }
    }

    private fun setupThoughtsEditTextScrolling() {
        binding.etThoughts.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                scrollToCursorPosition()
            }
        })

        binding.etThoughts.setOnTouchListener { v, event ->
            handleEditTextTouch(v, event)
        }
    }

    private fun scrollToCursorPosition() {
        val layout = binding.etThoughts.layout
        if (layout != null) {
            val cursorLine = layout.getLineForOffset(binding.etThoughts.selectionStart)
            val scrollY = layout.getLineTop(cursorLine) - binding.etThoughts.height / 2
            binding.etThoughts.scrollTo(0, Math.max(0, scrollY))
        }
    }

    private fun handleEditTextTouch(v: View, event: MotionEvent): Boolean {
        v.parent.requestDisallowInterceptTouchEvent(true)
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_UP -> {
                v.parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return false
    }

    private fun setupViews() {
        // Populate fields
        if (currentJournal.title.contains("<")) {
            binding.editTitleEt.setText(
                android.text.Html.fromHtml(
                    currentJournal.title,
                    android.text.Html.FROM_HTML_MODE_COMPACT
                )
            )
        } else {
            binding.editTitleEt.setText(currentJournal.title)
        }

        if (currentJournal.thoughts.contains("<")) {
            binding.etThoughts.setText(
                android.text.Html.fromHtml(
                    currentJournal.thoughts,
                    android.text.Html.FROM_HTML_MODE_COMPACT
                )
            )
        } else {
            binding.etThoughts.setText(currentJournal.thoughts)
        }

        binding.editUsernameTv.text = currentJournal.username
        binding.editDateTv.text = currentJournal.timeAdded

        // Set colors
        backgroundColorHelper?.setBackgroundColor(currentJournal.backgroundColor)

        // Load image
        Glide.with(this)
            .load(currentJournal.imageUrl)
            .placeholder(R.drawable.ic_camera)
            .into(binding.editDisplayImage)

        updatePinIcon()
    }

    private fun updatePinIcon() {
        binding.btnPin?.setImageResource(
            if (isPinned) R.drawable.ic_pin else R.drawable.ic_pin_outline
        )
    }

    private fun setupClickListeners() {
        binding.closeBtn.setOnClickListener {
            finish()
        }

        binding.btnCancelEdit.setOnClickListener {
            finish()
        }

        binding.editCameraButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, GALLERY_REQUEST_CODE)
        }

        binding.editDisplayImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, GALLERY_REQUEST_CODE)
        }

        binding.btnSaveEdit.setOnClickListener {
            updateJournal()
        }

        binding.saveBtn.setOnClickListener {
            updateJournal()
        }

        binding.btnPin?.setOnClickListener {
            isPinned = !isPinned
            updatePinIcon()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val selectedUri = data.data
            if (selectedUri != null) {
                showImageConfirmationDialog(selectedUri)
            }
        }
    }

    private fun showImageConfirmationDialog(selectedUri: Uri) {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)
        val dialogBinding = DialogConfirmImageBinding.inflate(layoutInflater)

        dialogBinding.btnConfirmYes.setOnClickListener {
            imageUri = selectedUri
            imageChanged = true
            binding.editDisplayImage.setImageURI(imageUri)
            dialog.dismiss()
        }

        dialogBinding.btnConfirmNo.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, GALLERY_REQUEST_CODE)
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun updateJournal() {
        val title = if (titleFormattingHelper != null) {
            android.text.Html.toHtml(
                binding.editTitleEt.text as android.text.Spanned,
                android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE
            )
        } else {
            binding.editTitleEt.text.toString()
        }

        val thoughts = if (thoughtsFormattingHelper != null) {
            android.text.Html.toHtml(
                binding.etThoughts.text as android.text.Spanned,
                android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE
            )
        } else {
            binding.etThoughts.text.toString()
        }

        if (TextUtils.isEmpty(title.trim())) {
            binding.editTitleEt.error = "Title is required"
            return
        }

        if (TextUtils.isEmpty(thoughts.trim())) {
            binding.etThoughts.error = "Thoughts are required"
            return
        }

        binding.editProgressBar.visibility = View.VISIBLE

        db.collection("Journal")
            .whereEqualTo(FIELD_USER_ID, currentJournal.userId)
            .whereEqualTo(FIELD_TITLE, currentJournal.title)
            .whereEqualTo(FIELD_TIME_ADDED, currentJournal.timeAdded)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val documentId = documents.documents[0].id
                    val isFromCache = documents.metadata.isFromCache

                    if (imageChanged && imageUri != null) {
                        if (isFromCache) {
                            Toast.makeText(
                                this,
                                "You're offline. Image change will be saved when online.",
                                Toast.LENGTH_LONG
                            ).show()
                            updateJournalData(documentId, title, thoughts, currentImageUrl)
                        } else {
                            deleteOldImageAndUploadNew(documentId, title, thoughts)
                        }
                    } else {
                        updateJournalData(documentId, title, thoughts, currentImageUrl)
                    }
                } else {
                    binding.editProgressBar.visibility = View.INVISIBLE
                    Toast.makeText(this, "Journal not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                handleUpdateError(e)
            }
    }

    private fun deleteOldImageAndUploadNew(documentId: String, title: String, thoughts: String) {
        try {
            val oldStorageRef = FirebaseStorage.getInstance().getReferenceFromUrl(currentImageUrl)
            oldStorageRef.delete()
                .addOnSuccessListener {
                    uploadNewImage(documentId, title, thoughts)
                }
                .addOnFailureListener {
                    uploadNewImage(documentId, title, thoughts)
                }
        } catch (e: Exception) {
            uploadNewImage(documentId, title, thoughts)
        }
    }

    private fun uploadNewImage(documentId: String, title: String, thoughts: String) {
        val filepath: StorageReference = storageReference
            .child("journal_images")
            .child("my_image_${Timestamp.now().seconds}")

        filepath.putFile(imageUri!!)
            .addOnSuccessListener {
                filepath.downloadUrl.addOnSuccessListener { uri ->
                    updateJournalData(documentId, title, thoughts, uri.toString())
                }
            }
            .addOnFailureListener { e ->
                binding.editProgressBar.visibility = View.INVISIBLE
                Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateJournalData(documentId: String, title: String, thoughts: String, imageUrl: String) {
        val updates = hashMapOf<String, Any>(
            FIELD_TITLE to title,
            FIELD_THOUGHTS to thoughts,
            FIELD_IMAGE_URL to imageUrl,
            FIELD_TEXT_COLOR to (thoughtsFormattingHelper?.textColor ?: "#000000"),
            FIELD_BACKGROUND_COLOR to (backgroundColorHelper?.backgroundColor ?: "#FFFFFF"),
            FIELD_IS_PINNED to isPinned
        )

        db.collection("Journal")
            .document(documentId)
            .update(updates)
            .addOnSuccessListener {
                binding.editProgressBar.visibility = View.INVISIBLE
                Toast.makeText(this, "Journal updated successfully", Toast.LENGTH_SHORT).show()

                val resultIntent = Intent().apply {
                    putExtra(FIELD_TITLE, title)
                    putExtra(FIELD_THOUGHTS, thoughts)
                    putExtra(FIELD_IMAGE_URL, imageUrl)
                    putExtra(FIELD_USER_ID, currentJournal.userId)
                    putExtra(FIELD_TIME_ADDED, currentJournal.timeAdded)
                    putExtra(FIELD_USERNAME, currentJournal.username)
                    putExtra(FIELD_TEXT_COLOR, thoughtsFormattingHelper?.textColor ?: "#000000")
                    putExtra(FIELD_BACKGROUND_COLOR, backgroundColorHelper?.backgroundColor ?: "#FFFFFF")
                    putExtra(FIELD_IS_PINNED, isPinned)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
            .addOnFailureListener { e ->
                handleUpdateError(e)
            }
    }

    private fun handleUpdateError(exception: Exception) {
        binding.editProgressBar.visibility = View.INVISIBLE
        when (exception) {
            is FirebaseFirestoreException -> {
                when (exception.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE -> {
                        Toast.makeText(this, "You're offline. Changes will sync when online.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    else -> {
                        Toast.makeText(this, "Failed to update: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}