package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
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

    private val allTextNotes = mutableListOf<UnifiedNoteItem>()
    private var isStaggeredLayout = true
    private var sortAscending = false

    private lateinit var adapter: UnifiedNotesAdapter

    private val TAG = "TextNotesListActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_text_notes_list)

        setupToolbar()

        firebaseAuth = FirebaseAuth.getInstance()
        user = firebaseAuth.currentUser!!
        collectionReference = db.collection("TextNotes")

        setupRecyclerView()
        setupSearchFunctionality()
        setupSortButton()
        setupLayoutToggle()

        binding.fabAddNote.setOnClickListener {
            startActivity(Intent(this, AddTextNoteActivity::class.java))
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.toolbarLayout.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = UnifiedNotesAdapter(this, mutableListOf())
        binding.textNotesRecyclerView.adapter = adapter
        setStaggeredLayout()
    }

    private fun setStaggeredLayout() {
        binding.textNotesRecyclerView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        isStaggeredLayout = true
        binding.toolbarLayout.btnLayoutToggle.setImageResource(R.drawable.ic_grid_view)
    }

    private fun setLinearLayout() {
        binding.textNotesRecyclerView.layoutManager = LinearLayoutManager(this)
        isStaggeredLayout = false
        binding.toolbarLayout.btnLayoutToggle.setImageResource(R.drawable.ic_list_view)
    }

    private fun setupLayoutToggle() {
        binding.toolbarLayout.btnLayoutToggle.setOnClickListener {
            if (isStaggeredLayout) {
                setLinearLayout()
            } else {
                setStaggeredLayout()
            }
        }
    }

    private fun setupSortButton() {
        updateSortIcon()

        binding.toolbarLayout.btnSort.setOnClickListener {
            sortAscending = !sortAscending
            updateSortIcon()
            applyCurrentFiltersAndSort()
        }
    }

    private fun updateSortIcon() {
        if (sortAscending) {
            binding.toolbarLayout.btnSort.setImageResource(R.drawable.ic_sort_ascending)
        } else {
            binding.toolbarLayout.btnSort.setImageResource(R.drawable.ic_sort_descending)
        }
    }

    private fun setupSearchFunctionality() {
        binding.toolbarLayout.edtTxtSrch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyCurrentFiltersAndSort()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyCurrentFiltersAndSort() {
        val query = binding.toolbarLayout.edtTxtSrch.text.toString()

        // Filter text notes based on search query
        val filtered = if (query.isEmpty()) {
            allTextNotes.toList()
        } else {
            val lowerQuery = query.lowercase()
            allTextNotes.filter { note ->
                when (note) {
                    is UnifiedNoteItem.TextNoteItem -> {
                        note.textNote.title.lowercase().contains(lowerQuery) ||
                                note.textNote.content.lowercase().contains(lowerQuery)
                    }
                    else -> false
                }
            }
        }

        // Sort filtered notes (pinned first, then by time)
        val sorted = if (sortAscending) {
            filtered.sortedWith(
                compareByDescending<UnifiedNoteItem> { it.isPinned }
                    .thenBy { it.timeAdded }
            )
        } else {
            filtered.sortedWith(
                compareByDescending<UnifiedNoteItem> { it.isPinned }
                    .thenByDescending { it.timeAdded }
            )
        }

        Log.d(TAG, "Applying filters: query='$query', sortAscending=$sortAscending")
        Log.d(TAG, "Filtered count: ${filtered.size}, Sorted count: ${sorted.size}")

        // Update adapter with sorted list
        adapter.updateListSimple(sorted)
        updateEmptyState(sorted.isEmpty(), query)
    }

    override fun onStart() {
        super.onStart()
        loadTextNotes()
    }

    override fun onStop() {
        super.onStop()
        adapter.releasePlayer()
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
                    allTextNotes.clear()

                    for (document in querySnapshot) {
                        val note = document.toObject(TextNote::class.java).copy(
                            id = document.id
                        )

                        val noteItem = UnifiedNoteItem.TextNoteItem(note)
                        allTextNotes.add(noteItem)
                    }

                    applyCurrentFiltersAndSort()
                } else {
                    allTextNotes.clear()
                    applyCurrentFiltersAndSort()
                }
            }
    }

    private fun updateEmptyState(isEmpty: Boolean, searchQuery: String) {
        if (isEmpty) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.textNotesRecyclerView.visibility = View.GONE

            binding.emptyStateMessage.text = if (searchQuery.isNotEmpty()) {
                "No text notes found for \"$searchQuery\""
            } else {
                "No text notes yet\nStart creating your first note"
            }
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.textNotesRecyclerView.visibility = View.VISIBLE
        }
    }
}