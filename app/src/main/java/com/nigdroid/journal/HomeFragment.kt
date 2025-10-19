package com.nigdroid.journal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nigdroid.journal.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: UnifiedNotesAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val allNotes = mutableListOf<UnifiedNoteItem>()
    private var isStaggeredLayout = true
    private var sortAscending = false // Default: descending (newest first)
    private var showOnlyPinned = true // Default: show only pinned notes
    private var isExpanded = false // Track if "See all" is expanded
    private var isFragmentAlive = false // Track fragment lifecycle

    private val TAG = "HomeFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(inflater, R.layout.fragment_home, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        isFragmentAlive = true

        firebaseAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        setupSwipeRefresh()
        loadUserProfile()
        setupNavigation()

        setupSortButton()
        setupLayoutToggle()
        setupSeeAllToggle()
        setupSearchFunctionality()

        // Load pinned notes by default
        loadPinnedNotes()

        binding.profileImage.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                startActivity(Intent(requireContext(), ProfileActivity::class.java))
            }
        }

        binding.chatbot.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                startActivity(Intent(requireContext(), GeminiActivity::class.java))
            }
        }

        return binding.root
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
            if (isAdded && isFragmentAlive) {
                refreshData()
            }
        }
    }

    private fun refreshData() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        // Reload user profile
        loadUserProfile()

        // Reload notes based on current view state
        if (showOnlyPinned) {
            loadPinnedNotes()
        } else {
            loadAllNotes()
        }

        // Stop refreshing animation after a short delay if it hasn't stopped
        binding.root.postDelayed({
            if (isAdded && isFragmentAlive && _binding != null && swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }
        }, 3000)
    }

    private fun setupRecyclerView() {
        adapter = UnifiedNotesAdapter(requireContext(), mutableListOf())
        binding.AllNotesRecyclerView.adapter = adapter
        setStaggeredLayout()
    }

    private fun setStaggeredLayout() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        binding.AllNotesRecyclerView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        isStaggeredLayout = true
        binding.btnLayoutToggle.setImageResource(R.drawable.ic_grid_view)
    }

    private fun setLinearLayout() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        binding.AllNotesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        isStaggeredLayout = false
        binding.btnLayoutToggle.setImageResource(R.drawable.ic_list_view)
    }

    private fun setupLayoutToggle() {
        binding.btnLayoutToggle.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                if (isStaggeredLayout) {
                    setLinearLayout()
                } else {
                    setStaggeredLayout()
                }
            }
        }
    }

    private fun setupSortButton() {
        // Set initial icon to descending (default)
        updateSortIcon()

        binding.btnSort.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                // Toggle sort order
                sortAscending = !sortAscending
                updateSortIcon()
                applyCurrentFiltersAndSort()
            }
        }
    }

    private fun updateSortIcon() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        if (sortAscending) {
            binding.btnSort.setImageResource(R.drawable.ic_sort_ascending)
        } else {
            binding.btnSort.setImageResource(R.drawable.ic_sort_descending)
        }
    }

    private fun setupSeeAllToggle() {
        val seeAllContainer = binding.root.findViewById<View>(R.id.seeAllContainer)

        seeAllContainer?.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                toggleNotesView()
            }
        }

        binding.tvSeeAll.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                toggleNotesView()
            }
        }

        binding.ivSeeAllArrow.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                toggleNotesView()
            }
        }

        updateSeeAllUI()
    }

    private fun toggleNotesView() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        if (showOnlyPinned) {
            isExpanded = true
            showOnlyPinned = false
            loadAllNotes()
        } else {
            isExpanded = false
            showOnlyPinned = true
            loadPinnedNotes()
        }

        animateArrow()
        updateSeeAllUI()
    }

    private fun animateArrow() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        val rotation = if (isExpanded) 180f else 0f
        binding.ivSeeAllArrow.animate()
            .rotation(rotation)
            .setDuration(200)
            .start()
    }

    private fun updateSeeAllUI() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        if (showOnlyPinned) {
            binding.tvSeeAll.text = "See all"
            binding.ivSeeAllArrow.rotation = 0f
        } else {
            binding.tvSeeAll.text = "Show less"
            binding.ivSeeAllArrow.rotation = 180f
        }
    }

    private fun setupSearchFunctionality() {
        binding.edtTxtSrch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isAdded && isFragmentAlive) {
                    applyCurrentFiltersAndSort()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyCurrentFiltersAndSort() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

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

        // Sort filtered notes
        val sorted = if (showOnlyPinned) {
            // When showing only pinned, all are pinned, so just sort by time
            if (sortAscending) {
                filtered.sortedBy { it.timeAdded }
            } else {
                filtered.sortedByDescending { it.timeAdded }
            }
        } else {
            // When showing all, sort pinned first, then by time
            if (sortAscending) {
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
        }

        Log.d(TAG, "Applying filters: query='$query', showOnlyPinned=$showOnlyPinned, sortAscending=$sortAscending")
        Log.d(TAG, "Filtered count: ${filtered.size}, Sorted count: ${sorted.size}")

        // Update adapter with sorted list
        adapter.updateListSimple(sorted)
        updateEmptyState(sorted.isEmpty())
    }

    private fun loadAllNotes() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        if (!isAdded || !isFragmentAlive || _binding == null) return

        binding.progressBar.visibility = View.VISIBLE
        allNotes.clear()

        var loadedCollections = 0
        val totalCollections = 4

        fun checkIfAllLoaded() {
            loadedCollections++
            if (loadedCollections == totalCollections) {
                if (isAdded && isFragmentAlive && _binding != null) {
                    binding.progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    Log.d(TAG, "All notes loaded: ${allNotes.size} items")
                    applyCurrentFiltersAndSort()
                }
            }
        }

        db.collection("Journal")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded || !isFragmentAlive) return@addOnSuccessListener

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

        db.collection("TextNotes")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded || !isFragmentAlive) return@addOnSuccessListener

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

        db.collection("TodoItems")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded || !isFragmentAlive) return@addOnSuccessListener

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

        db.collection("AudioNotes")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded || !isFragmentAlive) return@addOnSuccessListener

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

    private fun loadPinnedNotes() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        if (!isAdded || !isFragmentAlive || _binding == null) return

        binding.progressBar.visibility = View.VISIBLE
        allNotes.clear()

        var loadedCollections = 0
        val totalCollections = 4

        fun checkIfAllLoaded() {
            loadedCollections++
            if (loadedCollections == totalCollections) {
                if (isAdded && isFragmentAlive && _binding != null) {
                    binding.progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    Log.d(TAG, "Pinned notes loaded: ${allNotes.size} items")
                    applyCurrentFiltersAndSort()
                }
            }
        }

        db.collection("Journal")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("isPinned", true)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded || !isFragmentAlive) return@addOnSuccessListener

                for (doc in documents) {
                    val journal = doc.toObject(Journal::class.java)
                    allNotes.add(UnifiedNoteItem.JournalItem(journal = journal, id = doc.id))
                }
                Log.d(TAG, "Loaded ${documents.size()} pinned journals")
                checkIfAllLoaded()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading pinned journals", e)
                checkIfAllLoaded()
            }

        db.collection("TextNotes")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("isPinned", true)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded || !isFragmentAlive) return@addOnSuccessListener

                for (doc in documents) {
                    val textNote = doc.toObject(TextNote::class.java).copy(id = doc.id)
                    allNotes.add(UnifiedNoteItem.TextNoteItem(textNote))
                }
                Log.d(TAG, "Loaded ${documents.size()} pinned text notes")
                checkIfAllLoaded()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading pinned text notes", e)
                checkIfAllLoaded()
            }

        db.collection("TodoItems")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("isPinned", true)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded || !isFragmentAlive) return@addOnSuccessListener

                for (doc in documents) {
                    val todoItem = doc.toObject(TodoItem::class.java).copy(id = doc.id)
                    allNotes.add(UnifiedNoteItem.TodoItemWrapper(todoItem))
                }
                Log.d(TAG, "Loaded ${documents.size()} pinned todos")
                checkIfAllLoaded()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading pinned todos", e)
                checkIfAllLoaded()
            }

        db.collection("AudioNotes")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("isPinned", true)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded || !isFragmentAlive) return@addOnSuccessListener

                for (doc in documents) {
                    val audioNote = doc.toObject(AudioNote::class.java).copy(id = doc.id)
                    allNotes.add(UnifiedNoteItem.AudioNoteItem(audioNote))
                }
                Log.d(TAG, "Loaded ${documents.size()} pinned audio notes")
                checkIfAllLoaded()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading pinned audio notes", e)
                checkIfAllLoaded()
            }
    }

    private fun updateEmptyState(isEmpty: Boolean = allNotes.isEmpty()) {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        val emptyLayout = binding.root.findViewById<View>(R.id.emptyStateLayout)

        if (isEmpty) {
            emptyLayout?.visibility = View.VISIBLE
            binding.AllNotesRecyclerView.visibility = View.GONE

            val emptyMessage = binding.root.findViewById<android.widget.TextView>(R.id.emptyStateMessage)
            emptyMessage?.text = if (showOnlyPinned) {
                "No pinned notes yet\nPin your important notes to see them here"
            } else {
                "No notes yet\nStart creating your first note"
            }
        } else {
            emptyLayout?.visibility = View.GONE
            binding.AllNotesRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun loadUserProfile() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        val currentUser = firebaseAuth.currentUser

        if (currentUser == null) {
            binding.username.text = "Guest"
            return
        }

        val displayName = currentUser.displayName
        val email = currentUser.email

        val userName = when {
            !displayName.isNullOrEmpty() -> displayName.split(" ").firstOrNull() ?: displayName
            !email.isNullOrEmpty() -> email.substringBefore("@")
            else -> "User"
        }

        binding.username.text = userName

        val photoUrl = currentUser.photoUrl

        if (photoUrl != null) {
            context?.let { ctx ->
                Glide.with(ctx)
                    .load(photoUrl)
                    .placeholder(R.drawable.profile_image)
                    .error(R.drawable.profile_image)
                    .circleCrop()
                    .into(binding.profileImage)
            }
        } else {
            binding.profileImage.setImageResource(R.drawable.profile_image)
        }

        context?.let { ctx ->
            val sharedPref = ctx.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            sharedPref.edit().apply {
                putString("username", userName)
                putString("userEmail", email)
                putString("photoUrl", photoUrl?.toString())
                apply()
            }
        }
    }

    private fun setupNavigation() {
        binding.JournalCard.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                startActivity(Intent(requireContext(), JournalListActivity::class.java))
            }
        }

        binding.TodoCard.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                startActivity(Intent(requireContext(), TodoListActivity::class.java))
            }
        }

        binding.audioCard.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                startActivity(Intent(requireContext(), AudioNotesListActivity::class.java))
            }
        }

        binding.textCard.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                startActivity(Intent(requireContext(), TextNotesListActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAdded && isFragmentAlive) {
            loadUserProfile()

            if (showOnlyPinned) {
                loadPinnedNotes()
            } else {
                loadAllNotes()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        adapter.releasePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentAlive = false
        adapter.releasePlayer()
        _binding = null
    }
}