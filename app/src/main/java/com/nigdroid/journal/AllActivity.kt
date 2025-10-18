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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nigdroid.journal.databinding.ActivityAllBinding

class AllActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: UnifiedNotesAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val allNotes = mutableListOf<UnifiedNoteItem>()
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
        setupSwipeRefresh()
        setupSortButton()
        setupLayoutToggle()
        setupSearchFunctionality()

        // Load all notes on activity start
        loadAllNotes()
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout = binding.swipeRefreshLayout

        // Customize refresh colors
        swipeRefreshLayout.setColorSchemeResources(
            R.color.purple_500,
            R.color.teal_200,
            R.color.purple_700
        )

        swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
    }

    private fun refreshData() {
        // Clear search if any
        binding.edtTxtSrch.text?.clear()

        // Reload all notes
        loadAllNotes()

        // Stop refreshing animation after a short delay if it hasn't stopped
        binding.root.postDelayed({
            if (swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }
        }, 3000)
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
        adapter = UnifiedNotesAdapter(this, mutableListOf())
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
            applyCurrentFiltersAndSort()
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
                applyCurrentFiltersAndSort()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyCurrentFiltersAndSort() {
        val query = binding.edtTxtSrch.text.toString()

        // Filter notes based on search query
        val filtered = if (query.isEmpty()) {
            allNotes.toList()
        } else {
            val lowerQuery = query.lowercase()
            allNotes.filter { note ->
                when (note) {
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

    private fun loadAllNotes() {
        val currentUserId = firebaseAuth.currentUser?.uid

        if (currentUserId == null) {
            Log.e(TAG, "User not authenticated")
            updateEmptyState(isEmpty = true, searchQuery = "")
            swipeRefreshLayout.isRefreshing = false
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
                swipeRefreshLayout.isRefreshing = false
                Log.d(TAG, "All notes loaded: ${allNotes.size} items")
                applyCurrentFiltersAndSort()
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
                Log.d(TAG, "Loaded ${documents.size()} journals")
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
                Log.d(TAG, "Loaded ${documents.size()} text notes")
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
                Log.d(TAG, "Loaded ${documents.size()} todos")
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
                Log.d(TAG, "Loaded ${documents.size()} audio notes")
                checkIfAllLoaded()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading audio notes", e)
                checkIfAllLoaded()
            }
    }

    private fun updateEmptyState(isEmpty: Boolean, searchQuery: String) {
        val emptyLayout = binding.emptyStateLayout

        if (isEmpty) {
            emptyLayout.visibility = View.VISIBLE
            binding.AllNotesRecyclerView.visibility = View.GONE

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