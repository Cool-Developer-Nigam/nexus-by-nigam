package com.nigdroid.journal

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.nigdroid.journal.databinding.ActivityEditJournalBinding
import com.nigdroid.journal.databinding.DialogConfirmImageBinding
import java.text.SimpleDateFormat
import java.util.*

class EditJournalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditJournalBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var storageReference: StorageReference
    private val auth = FirebaseAuth.getInstance()

    private var imageUri: Uri? = null
    private var currentImageUrl: String = ""
    private lateinit var currentJournal: Journal
    private var imageChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window transparent and show content behind
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Enable blur effect on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            )
            window.attributes.blurBehindRadius = 20
        }

        binding = DataBindingUtil.setContentView(this, R.layout.activity_edit_journal)

        // Setup thoughts EditText scrolling behavior
        setupThoughtsEditTextScrolling()

        storageReference = FirebaseStorage.getInstance().reference

        // Get journal data from intent
        currentJournal = Journal(
            intent.getStringExtra("title") ?: "",
            intent.getStringExtra("thoughts") ?: "",
            intent.getStringExtra("imageUrl") ?: "",
            intent.getStringExtra("userId") ?: "",
            intent.getStringExtra("timeAdded") ?: "",
            intent.getStringExtra("username") ?: ""
        )

        currentImageUrl = currentJournal.imageUrl

        setupViews()
        setupClickListeners()
        setupDismissOnOutsideClick()
    }

    private fun setupThoughtsEditTextScrolling() {
        binding.etThoughts.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                scrollToCursorPosition()
            }
        })

        // Allow EditText to scroll independently
        binding.etThoughts.setOnTouchListener { v, event ->
            handleEditTextTouch(v, event)
        }
    }

    /**
     * Scrolls the thoughts EditText to show the current cursor position
     * Centers the cursor line in the visible area
     */
    private fun scrollToCursorPosition() {
        val layout = binding.etThoughts.layout
        if (layout != null) {
            // Get the line where cursor is currently positioned
            val cursorLine = layout.getLineForOffset(binding.etThoughts.selectionStart)
            // Calculate scroll position to center the cursor line
            val scrollY = layout.getLineTop(cursorLine) - binding.etThoughts.height / 2
            // Scroll to the calculated position (ensuring it's not negative)
            binding.etThoughts.scrollTo(0, Math.max(0, scrollY))
        }
    }

    /**
     * Handles touch events for the thoughts EditText
     * Prevents parent views from intercepting scroll events
     * @return false to allow normal touch event handling
     */
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
        // Populate fields with current journal data
        binding.editTitleEt.setText(currentJournal.title)
        binding.etThoughts.setText(currentJournal.thoughts)
        binding.editUsernameTv.text = currentJournal.username
        binding.editDateTv.text = currentJournal.timeAdded

        // Load existing image
        Glide.with(this)
            .load(currentJournal.imageUrl)
            .placeholder(R.drawable.ic_camera)
            .into(binding.editDisplayImage)
    }

    private fun setupClickListeners() {
        // Close button
        binding.closeBtn.setOnClickListener {
            finish()
        }

        // Cancel button
        binding.btnCancelEdit.setOnClickListener {
            finish()
        }

        // Camera button - change image
        binding.editCameraButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, 1)
        }

        // Also allow clicking on the image to change it
        binding.editDisplayImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, 1)
        }

        // Save button
        binding.btnSaveEdit.setOnClickListener {
            updateJournal()
        }
        binding.saveBtn.setOnClickListener {
            updateJournal()
        }
    }

    private fun setupDismissOnOutsideClick() {
        // Dismiss when clicking outside the card
        binding.rootLayout.setOnClickListener {
            finish()
        }

        // Prevent clicks on the card from dismissing
        binding.editCard.setOnClickListener {
            // Do nothing - consume the click
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
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

            // Show the new image
            binding.editDisplayImage.setImageURI(imageUri)

            dialog.dismiss()
        }

        dialogBinding.btnConfirmNo.setOnClickListener {
            dialog.dismiss()
            // Reopen gallery
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, 1)
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun updateJournal() {
        val title = binding.editTitleEt.text.toString().trim()
        val thoughts = binding.etThoughts.text.toString().trim()

        // Validate inputs
        if (TextUtils.isEmpty(title)) {
            binding.editTitleEt.error = "Title is required"
            return
        }

        if (TextUtils.isEmpty(thoughts)) {
            binding.etThoughts.error = "Thoughts are required"
            return
        }

        binding.editProgressBar.visibility = View.VISIBLE

        // Query to find the document
        db.collection("Journal")
            .whereEqualTo("userId", currentJournal.userId)
            .whereEqualTo("title", currentJournal.title)
            .whereEqualTo("timeAdded", currentJournal.timeAdded)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val documentId = documents.documents[0].id

                    if (imageChanged && imageUri != null) {
                        // Delete old image and upload new one
                        deleteOldImageAndUploadNew(documentId, title, thoughts)
                    } else {
                        // Just update text fields, keep same image
                        updateJournalData(documentId, title, thoughts, currentImageUrl)
                    }
                } else {
                    binding.editProgressBar.visibility = View.INVISIBLE
                    Toast.makeText(this, "Journal not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.editProgressBar.visibility = View.INVISIBLE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteOldImageAndUploadNew(documentId: String, title: String, thoughts: String) {
        // Delete old image from storage
        try {
            val oldStorageRef = FirebaseStorage.getInstance().getReferenceFromUrl(currentImageUrl)
            oldStorageRef.delete()
                .addOnSuccessListener {
                    // Upload new image
                    uploadNewImage(documentId, title, thoughts)
                }
                .addOnFailureListener {
                    // Even if delete fails, upload new image
                    uploadNewImage(documentId, title, thoughts)
                }
        } catch (e: Exception) {
            // If URL is invalid, just upload new image
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
                    val newImageUrl = uri.toString()
                    updateJournalData(documentId, title, thoughts, newImageUrl)
                }
            }
            .addOnFailureListener { e ->
                binding.editProgressBar.visibility = View.INVISIBLE
                Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateJournalData(documentId: String, title: String, thoughts: String, imageUrl: String) {
        val updates = hashMapOf<String, Any>(
            "title" to title,
            "thoughts" to thoughts,
            "imageUrl" to imageUrl
        )

        db.collection("Journal")
            .document(documentId)
            .update(updates)
            .addOnSuccessListener {
                binding.editProgressBar.visibility = View.INVISIBLE
                Toast.makeText(this, "Journal updated successfully", Toast.LENGTH_SHORT).show()

                // Return to Show_Journal with updated data
                val resultIntent = Intent().apply {
                    putExtra("title", title)
                    putExtra("thoughts", thoughts)
                    putExtra("imageUrl", imageUrl)
                    putExtra("userId", currentJournal.userId)
                    putExtra("timeAdded", currentJournal.timeAdded)
                    putExtra("username", currentJournal.username)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
            .addOnFailureListener { e ->
                binding.editProgressBar.visibility = View.INVISIBLE
                Toast.makeText(this, "Failed to update journal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}