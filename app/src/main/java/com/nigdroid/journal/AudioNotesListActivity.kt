package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nigdroid.journal.databinding.ActivityAudioNotesListBinding
import com.nigdroid.journal.databinding.DialogConfirmSignoutBinding

class AudioNotesListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioNotesListBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var toolbar: Toolbar
    private lateinit var user: FirebaseUser
    private val db = FirebaseFirestore.getInstance()
    private lateinit var collectionReference: CollectionReference

    private val pinnedNotes = mutableListOf<UnifiedNoteItem>()
    private val unpinnedNotes = mutableListOf<UnifiedNoteItem>()

    private lateinit var pinnedAdapter: UnifiedNotesAdapter
    private lateinit var unpinnedAdapter: UnifiedNotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_audio_notes_list)

        // Setup toolbar
        setupCustomToolbar()

        // Firebase auth
        firebaseAuth = FirebaseAuth.getInstance()
        user = firebaseAuth.currentUser!!
        collectionReference = db.collection("AudioNotes")

        setupRecyclerViews()

        // FAB click listener
        binding.fabAddAudio.setOnClickListener {
            startActivity(Intent(this, AddAudioNoteActivity::class.java))
        }
    }

    private fun setupCustomToolbar() {
        toolbar = binding.toolbarLayout.toolbar
        setSupportActionBar(toolbar)

        // Hide default title
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.toolbarLayout.signout.setOnClickListener {
            showDeleteConfirmationDialog()
        }
        binding.toolbarLayout.backBtn.setOnClickListener {
            onBackPressed()
        }
    }

    private fun showDeleteConfirmationDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)

        // Use data binding
        val dialogBinding = DialogConfirmSignoutBinding.inflate(layoutInflater)

        dialogBinding.btnDelete.setOnClickListener {
            firebaseAuth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            finish()
        }

        dialogBinding.btnCancel.setOnClickListener {
            Toast.makeText(this, "Signout Cancelled", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun setupRecyclerViews() {
        // Pinned notes
        binding.pinnedRecyclerView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        pinnedAdapter = UnifiedNotesAdapter(this, pinnedNotes)
        binding.pinnedRecyclerView.adapter = pinnedAdapter

        // Unpinned notes
        binding.audioNotesRecyclerView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        unpinnedAdapter = UnifiedNotesAdapter(this, unpinnedNotes)
        binding.audioNotesRecyclerView.adapter = unpinnedAdapter
    }

    override fun onStart() {
        super.onStart()
        loadAudioNotes()
    }

    override fun onStop() {
        super.onStop()
        // Release media player resources
        pinnedAdapter.releasePlayer()
        unpinnedAdapter.releasePlayer()
    }

    private fun loadAudioNotes() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE

        collectionReference
            .whereEqualTo("userId", user.uid)
            .orderBy("timeModified", Query.Direction.DESCENDING)
            .addSnapshotListener { querySnapshot, error ->
                binding.progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    pinnedNotes.clear()
                    unpinnedNotes.clear()

                    for (document in querySnapshot) {
                        val note = document.toObject(AudioNote::class.java).copy(
                            id = document.id
                        )

                        val noteItem = UnifiedNoteItem.AudioNoteItem(note)

                        if (note.isPinned) {
                            pinnedNotes.add(noteItem)
                        } else {
                            unpinnedNotes.add(noteItem)
                        }
                    }

                    updateUI()
                } else {
                    pinnedNotes.clear()
                    unpinnedNotes.clear()
                    updateUI()
                }
            }
    }

    private fun updateUI() {
        // Pinned section
        if (pinnedNotes.isEmpty()) {
            binding.pinnedSection.visibility = View.GONE
        } else {
            binding.pinnedSection.visibility = View.VISIBLE
            pinnedAdapter.notifyDataSetChanged()
        }

        // Unpinned section
        if (unpinnedNotes.isEmpty() && pinnedNotes.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.othersSection.visibility = View.GONE
        } else if (unpinnedNotes.isEmpty()) {
            binding.othersSection.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.GONE
        } else {
            binding.othersSection.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE

            if (pinnedNotes.isEmpty()) {
                binding.othersSectionTitle.visibility = View.GONE
            } else {
                binding.othersSectionTitle.visibility = View.VISIBLE
            }

            unpinnedAdapter.notifyDataSetChanged()
        }
    }
}