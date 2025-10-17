package com.nigdroid.journal

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nigdroid.journal.databinding.ActivityAllBinding

class AllActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: UnifiedNotesAdapter

    private val allNotes = mutableListOf<UnifiedNoteItem>()
    private val filteredNotes = mutableListOf<UnifiedNoteItem>()
    private var isStaggeredLayout = true
    private var sortAscending = false // Default: descending (newest first)

    private val TAG = "AllActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_all)

        // Handle edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        firebaseAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupToolbar()
        setupRecyclerView()
        setupSortButton()
        setupLayoutToggle()
        setupSearchFunctionality()

        // Load all notes on activity start
        loadAllNotes()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "All Notes"
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = UnifiedNotesAdapter(this, filteredNotes)
        binding.AllNotesRecyclerView.adapter = adapter
        setStaggeredLayout()
    }

    private fun setStaggeredLayout() {
        binding.AllNotesRecyclerView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        isStaggeredLayout = true
        binding.btnLayoutToggle.setImageResource(R.drawable.ic_grid_view)
    }

    private fun setLinearLayout() {
        binding.AllNotesRecyclerView.layoutManager = LinearLayoutManager(this)
        isStaggeredLayout = false
        binding.btnLayoutToggle.setImageResource(R.drawable.ic_list_view)
    }

    private fun setupLayoutToggle() {
        binding.btnLayoutToggle.setOnClickListener {
            if (isStaggeredLayout) {
                setLinearLayout()
            } else {
                setStaggeredLayout()
            }
        }
    }

    private fun setupSortButton() {
        // Set initial icon to descending (default)
        updateSortIcon()

        binding.btnSort.setOnClickListener {
            // Toggle sort order
            sortAscending = !sortAscending
            updateSortIcon()
            sortAndUpdateNotes()
        }
    }

    private fun updateSortIcon() {
        if (sortAscending) {
            binding.btnSort.setImageResource(R.drawable.ic_sort_ascending)
        } else {
            binding.btnSort.setImageResource(R.drawable.ic_sort_descending)
        }
    }

    private fun setupSearchFunctionality() {
        binding.edtTxtSrch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterNotes(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterNotes(query: String) {
        filteredNotes.clear()

        if (query.isEmpty()) {
            filteredNotes.addAll(allNotes)
        } else {
            val lowerQuery = query.lowercase()

            for (note in allNotes) {
                val matchesQuery = when (note) {
                    is UnifiedNoteItem.JournalItem -> {
                        note.journal.title.lowercase().contains(lowerQuery) ||
                                note.journal.thoughts.lowercase().contains(lowerQuery) ||
                                note.journal.username.lowercase().contains(lowerQuery)
                    }
                    is UnifiedNoteItem.TextNoteItem -> {
                        note.textNote.title.lowercase().contains(lowerQuery) ||
                                note.textNote.content.lowercase().contains(lowerQuery)
                    }
                    is UnifiedNoteItem.TodoItemWrapper -> {
                        note.todoItem.title.lowercase().contains(lowerQuery) ||
                                note.todoItem.items.any { it.text.lowercase().contains(lowerQuery) }
                    }
                    is UnifiedNoteItem.AudioNoteItem -> {
                        note.audioNote.title.lowercase().contains(lowerQuery) ||
                                note.audioNote.transcription.lowercase().contains(lowerQuery)
                    }
                }

                if (matchesQuery) {
                    filteredNotes.add(note)
                }
            }
        }

        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun loadAllNotes() {
        val currentUserId = firebaseAuth.currentUser?.uid

        if (currentUserId == null) {
            Log.e(TAG, "User not authenticated")
            updateEmptyState()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        allNotes.clear()

        var loadedCollections = 0
        val totalCollections = 4

        fun checkIfAllLoaded() {
            loadedCollections++
            if (loadedCollections == totalCollections) {
                binding.progressBar.visibility = View.GONE
                sortAndUpdateNotes()
            }
        }

        // Load Journals
        db.collection("Journal")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val journal = doc.toObject(Journal::class.java)
                    allNotes.add(UnifiedNoteItem.JournalItem(journal = journal, id = doc.id))
                }
                checkIfAllLoaded()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading journals", e)
                checkIfAllLoaded()
            }

        // Load Text Notes
        db.collection("TextNotes")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val textNote = doc.toObject(TextNote::class.java).copy(id = doc.id)
                    allNotes.add(UnifiedNoteItem.TextNoteItem(textNote))
                }
                checkIfAllLoaded()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading text notes", e)
                checkIfAllLoaded()
            }

        // Load Todo Items
        db.collection("TodoItems")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val todoItem = doc.toObject(TodoItem::class.java).copy(id = doc.id)
                    allNotes.add(UnifiedNoteItem.TodoItemWrapper(todoItem))
                }
                checkIfAllLoaded()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading todos", e)
                checkIfAllLoaded()
            }

        // Load Audio Notes
        db.collection("AudioNotes")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val audioNote = doc.toObject(AudioNote::class.java).copy(id = doc.id)
                    allNotes.add(UnifiedNoteItem.AudioNoteItem(audioNote))
                }
                checkIfAllLoaded()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading audio notes", e)
                checkIfAllLoaded()
            }
    }

    private fun sortAndUpdateNotes() {
        val sortedNotes = if (sortAscending) {
            allNotes.sortedWith(
                compareByDescending<UnifiedNoteItem> { it.isPinned }
                    .thenBy { it.timeAdded }
            )
        } else {
            allNotes.sortedWith(
                compareByDescending<UnifiedNoteItem> { it.isPinned }
                    .thenByDescending { it.timeAdded }
            )
        }

        allNotes.clear()
        allNotes.addAll(sortedNotes)

        filterNotes(binding.edtTxtSrch.text.toString())
    }

    private fun updateEmptyState() {
        val emptyLayout = binding.emptyStateLayout

        if (filteredNotes.isEmpty()) {
            emptyLayout.visibility = View.VISIBLE
            binding.AllNotesRecyclerView.visibility = View.GONE

            val searchQuery = binding.edtTxtSrch.text.toString()
            binding.emptyStateMessage.text = if (searchQuery.isNotEmpty()) {
                "No notes found for \"$searchQuery\""
            } else {
                "No notes yet\nStart creating your first note"
            }
        } else {
            emptyLayout.visibility = View.GONE
            binding.AllNotesRecyclerView.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload notes when returning to activity
        loadAllNotes()
    }

    override fun onPause() {
        super.onPause()
        // Release media player if any
        adapter.releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.releasePlayer()
    }
}