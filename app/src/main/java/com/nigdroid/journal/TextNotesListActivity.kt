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
import com.nigdroid.journal.databinding.ActivityTextNotesListBinding
import com.nigdroid.journal.databinding.DialogConfirmSignoutBinding

class TextNotesListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextNotesListBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var user: FirebaseUser
    private val db = FirebaseFirestore.getInstance()
    private lateinit var collectionReference: CollectionReference

    private lateinit var toolbar: Toolbar

    private val pinnedNotes = mutableListOf<TextNote>()
    private val unpinnedNotes = mutableListOf<TextNote>()

    private lateinit var pinnedAdapter: TextNotesRecyclerAdapter
    private lateinit var unpinnedAdapter: TextNotesRecyclerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_text_notes_list)

        setupCustomToolbar()

        firebaseAuth = FirebaseAuth.getInstance()
        user = firebaseAuth.currentUser!!
        collectionReference = db.collection("TextNotes")

        setupRecyclerViews()

        binding.fabAddNote.setOnClickListener {
            startActivity(Intent(this, AddTextNoteActivity::class.java))
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
        binding.pinnedRecyclerView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        pinnedAdapter = TextNotesRecyclerAdapter(this, pinnedNotes) { note ->
            openNoteDetail(note)
        }
        binding.pinnedRecyclerView.adapter = pinnedAdapter

        binding.textNotesRecyclerView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        unpinnedAdapter = TextNotesRecyclerAdapter(this, unpinnedNotes) { note ->
            openNoteDetail(note)
        }
        binding.textNotesRecyclerView.adapter = unpinnedAdapter
    }

    override fun onStart() {
        super.onStart()
        loadTextNotes()
    }

    private fun loadTextNotes() {
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
                        val note = document.toObject(TextNote::class.java).copy(
                            id = document.id
                        )

                        if (note.isPinned) {
                            pinnedNotes.add(note)
                        } else {
                            unpinnedNotes.add(note)
                        }
                    }

                    if (pinnedNotes.isEmpty()) {
                        binding.pinnedSection.visibility = View.GONE
                    } else {
                        binding.pinnedSection.visibility = View.VISIBLE
                        pinnedAdapter.notifyDataSetChanged()
                    }

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

                } else {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    binding.pinnedSection.visibility = View.GONE
                    binding.othersSection.visibility = View.GONE
                }
            }
    }

    private fun openNoteDetail(note: TextNote) {
        val intent = Intent(this, AddTextNoteActivity::class.java).apply {
            putExtra("NOTE_ID", note.id)
            putExtra("NOTE_TITLE", note.title)
            putExtra("IS_PINNED", note.isPinned)
        }
        startActivity(intent)
    }
}