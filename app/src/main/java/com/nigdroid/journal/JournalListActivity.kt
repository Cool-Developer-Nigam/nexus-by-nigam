package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.nigdroid.journal.databinding.ActivityJournalListBinding
import com.nigdroid.journal.databinding.DialogConfirmSignoutBinding

class JournalListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJournalListBinding

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var user: FirebaseUser
    private var db = FirebaseFirestore.getInstance()
    private var collectionReference: CollectionReference = db.collection("Journal")

    private val allJournals = mutableListOf<UnifiedNoteItem>()
    private var isStaggeredLayout = true
    private var sortAscending = false // Default: descending (newest first)

    private lateinit var adapter: UnifiedNotesAdapter
    private var listenerRegistration: ListenerRegistration? = null

    companion object {
        private const val TAG = "JournalListActivity"
        private const val PREFS_NAME = "MyAppPrefs"
        private const val KEY_USERNAME = "username"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_journal_list)

        setupToolbar()

        firebaseAuth = Firebase.auth
        user = firebaseAuth.currentUser!!

        setupRecyclerView()
        setupSearchFunctionality()
        setupSortButton()
        setupLayoutToggle()

        binding.floatingActionButton.setOnClickListener {
            startActivity(Intent(this, AddJournalActivity::class.java))
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
        binding.recyclerView.adapter = adapter
        setStaggeredLayout()
    }

    private fun setStaggeredLayout() {
        binding.recyclerView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        isStaggeredLayout = true
        binding.toolbarLayout.btnLayoutToggle.setImageResource(R.drawable.ic_grid_view)
    }

    private fun setLinearLayout() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
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

        // Filter journals based on search query
        val filtered = if (query.isEmpty()) {
            allJournals.toList()
        } else {
            val lowerQuery = query.lowercase()
            allJournals.filter { note ->
                when (note) {
                    is UnifiedNoteItem.JournalItem -> {
                        note.journal.title.lowercase().contains(lowerQuery) ||
                                note.journal.thoughts.lowercase().contains(lowerQuery) ||
                                note.journal.username.lowercase().contains(lowerQuery)
                    }
                    else -> false
                }
            }
        }

        // Sort filtered journals (pinned first, then by time)
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
        loadJournalsWithOfflineSupport()
    }

    override fun onStop() {
        super.onStop()
        listenerRegistration?.remove()
        adapter.releasePlayer()
    }

    private fun loadJournalsWithOfflineSupport() {
        binding.progressBar.visibility = View.VISIBLE
        binding.NoPostTv.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE

        allJournals.clear()
        loadFromCache()
    }

    private fun loadFromCache() {
        collectionReference
            .whereEqualTo("userId", user.uid)
            .orderBy("timeAdded", Query.Direction.DESCENDING)
            .get(Source.CACHE)
            .addOnSuccessListener { querySnapshot ->
                Log.d(TAG, "Loaded ${querySnapshot.size()} journals from cache")

                if (!querySnapshot.isEmpty) {
                    val journals = querySnapshot.documents.mapNotNull { document ->
                        try {
                            document.toObject(Journal::class.java) to document.id
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing journal from cache", e)
                            null
                        }
                    }

                    processJournals(journals as List<Pair<Journal, String>>)

                    querySnapshot.documents.firstOrNull()?.data?.get("username")?.toString()?.let {
                        saveUsername(it)
                    }
                }

                setupRealtimeListener()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Cache load failed, loading from server", e)
                setupRealtimeListener()
            }
    }

    private fun setupRealtimeListener() {
        listenerRegistration = collectionReference
            .whereEqualTo("userId", user.uid)
            .orderBy("timeAdded", Query.Direction.DESCENDING)
            .addSnapshotListener { querySnapshot, error ->
                binding.progressBar.visibility = View.GONE

                if (error != null) {
                    handleLoadError(error)
                    return@addSnapshotListener
                }

                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    val isFromCache = querySnapshot.metadata.isFromCache

                    if (isFromCache) {
                        Log.d(TAG, "Data loaded from offline cache")
                    } else {
                        Log.d(TAG, "Data loaded from server")
                    }

                    querySnapshot.documents.firstOrNull()?.data?.get("username")?.toString()?.let {
                        saveUsername(it)
                    }

                    val journals = querySnapshot.documents.mapNotNull { document ->
                        try {
                            document.toObject(Journal::class.java) to document.id
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing journal", e)
                            null
                        }
                    }

                    processJournals(journals as List<Pair<Journal, String>>)
                } else {
                    allJournals.clear()
                    applyCurrentFiltersAndSort()

                    if (querySnapshot?.metadata?.isFromCache == true) {
                        Log.d(TAG, "No cached journals found")
                    } else {
                        Log.d(TAG, "No journals found on server")
                    }
                }
            }
    }

    private fun processJournals(journals: List<Pair<Journal, String>>) {
        allJournals.clear()

        for ((journal, documentId) in journals) {
            val journalItem = UnifiedNoteItem.JournalItem(journal = journal, id = documentId)
            allJournals.add(journalItem)
        }

        applyCurrentFiltersAndSort()
    }

    private fun updateEmptyState(isEmpty: Boolean, searchQuery: String) {
        if (isEmpty) {
            binding.NoPostTv.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE

            binding.emptyStateMessage.text = if (searchQuery.isNotEmpty()) {
                "No journals found for \"$searchQuery\""
            } else {
                "No journals yet\nStart writing your first journal"
            }
        } else {
            binding.NoPostTv.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun saveUsername(username: String) {
        if (username.isNotEmpty() && username != "null") {
            val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            sharedPref.edit {
                putString(KEY_USERNAME, username)
            }
        }
    }

    private fun handleLoadError(exception: Exception) {
        when (exception) {
            is FirebaseFirestoreException -> {
                when (exception.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE -> {
                        Log.w(TAG, "Network unavailable, showing cached data", exception)
                        Toast.makeText(
                            this,
                            "You're offline. Showing cached data.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION -> {
                        Log.e(TAG, "Index required. Check console for index creation link.", exception)
                        Toast.makeText(
                            this,
                            "Database needs optimization. Please check logs.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        Log.e(TAG, "Error loading journals: ${exception.message}", exception)
                        Toast.makeText(
                            this,
                            "Error loading journals: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            else -> {
                Log.e(TAG, "Unexpected error loading journals", exception)
                Toast.makeText(
                    this,
                    "Error loading journals: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        if (allJournals.isEmpty()) {
            binding.NoPostTv.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        }
    }

    fun onJournalDeleted() {
        if (allJournals.isEmpty()) {
            binding.NoPostTv.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        }
    }
}