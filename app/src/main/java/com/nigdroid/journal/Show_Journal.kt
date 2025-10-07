package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.nigdroid.journal.databinding.ActivityShowJournalBinding
import com.nigdroid.journal.databinding.DialogConfirmDeleteBinding

class Show_Journal : AppCompatActivity() {

    private lateinit var binding: ActivityShowJournalBinding
    private lateinit var toolbar: Toolbar
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private lateinit var currentJournal: Journal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_show_journal)

        // Get journal data from intent
        currentJournal = Journal(
            intent.getStringExtra("title") ?: "",
            intent.getStringExtra("thoughts") ?: "",
            intent.getStringExtra("imageUrl") ?: "",
            intent.getStringExtra("userId") ?: "",
            intent.getStringExtra("timeAdded") ?: "",
            intent.getStringExtra("username") ?: ""
        )

        binding.journal = currentJournal
        binding.lifecycleOwner = this

        val username = currentJournal.username.trim() // trim whitespace at the ends
        val firstWord = username.split(" ")[0]
        binding.tvUsername.text = "Hello $firstWord"

//        toolbar setup
        setupCustomToolbar()
    }

    private fun setupCustomToolbar() {
        toolbar = binding.toolbarLayout.toolbar
        setSupportActionBar(toolbar)

        // Hide default title
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Back button
        binding.toolbarLayout.backBtn.setOnClickListener {
            finish()
        }

        // Edit button
        binding.toolbarLayout.editBtn.setOnClickListener {
            val intent = Intent(this, EditJournalActivity::class.java).apply {
                putExtra("title", currentJournal.title)
                putExtra("thoughts", currentJournal.thoughts)
                putExtra("imageUrl", currentJournal.imageUrl)
                putExtra("userId", currentJournal.userId)
                putExtra("timeAdded", currentJournal.timeAdded)
                putExtra("username", currentJournal.username)
            }
            startActivityForResult(intent, EDIT_REQUEST_CODE)
        }
        // Delete button
        binding.toolbarLayout.deleteBtn.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    companion object {
        private const val EDIT_REQUEST_CODE = 100
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EDIT_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Update the current journal with edited data
            currentJournal = Journal(
                data.getStringExtra("title") ?: currentJournal.title,
                data.getStringExtra("thoughts") ?: currentJournal.thoughts,
                data.getStringExtra("imageUrl") ?: currentJournal.imageUrl,
                data.getStringExtra("userId") ?: currentJournal.userId,
                data.getStringExtra("timeAdded") ?: currentJournal.timeAdded,
                data.getStringExtra("username") ?: currentJournal.username
            )

            // Refresh the UI with updated data
            binding.journal = currentJournal
            binding.executePendingBindings()
        }

    }

    private fun showDeleteConfirmationDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)

        // Use data binding
        val dialogBinding = DialogConfirmDeleteBinding.inflate(LayoutInflater.from(this))

        dialogBinding.btnDelete.setOnClickListener {
            deleteJournal()
            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun deleteJournal() {
        // Query Firestore to find the document with matching data
        db.collection("Journal")
            .whereEqualTo("userId", currentJournal.userId)
            .whereEqualTo("title", currentJournal.title)
            .whereEqualTo("timeAdded", currentJournal.timeAdded)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val documentId = documents.documents[0].id

                    // Delete from Firestore
                    db.collection("Journal")
                        .document(documentId)
                        .delete()
                        .addOnSuccessListener {
                            // Delete image from Storage
                            deleteImageFromStorage(currentJournal.imageUrl)

                            Toast.makeText(
                                this,
                                "Journal deleted successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Go back to journal list
                            startActivity(Intent(this, JournalListActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "Failed to delete journal: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    Toast.makeText(
                        this,
                        "Journal not found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Error finding journal: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun deleteImageFromStorage(imageUrl: String) {
        if (imageUrl.isNotEmpty()) {
            try {
                val storageRef = storage.getReferenceFromUrl(imageUrl)
                storageRef.delete()
                    .addOnSuccessListener {
                        // Image deleted successfully
                    }
                    .addOnFailureListener { e ->
                        // Handle error silently
                    }
            } catch (e: Exception) {
                // Handle invalid URL
            }
        }
    }
}