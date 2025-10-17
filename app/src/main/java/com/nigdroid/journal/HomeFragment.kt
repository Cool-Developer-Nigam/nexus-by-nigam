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
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nigdroid.journal.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: UnifiedNotesAdapter

    private val allNotes = mutableListOf<UnifiedNoteItem>()
    private val filteredNotes = mutableListOf<UnifiedNoteItem>()
    private var isStaggeredLayout = true
    private var sortAscending = false // Default: descending (newest first)
    private var showOnlyPinned = true // Default: show only pinned notes
    private var isExpanded = false // Track if "See all" is expanded

    private val TAG = "HomeFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_home, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        firebaseAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        loadUserProfile()
        setupNavigation()

        setupSortButton()
        setupLayoutToggle()
        setupSeeAllToggle()
        setupSearchFunctionality()

        // Load pinned notes by default
        loadPinnedNotes()

        binding.profileImage.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        binding.chatbot.setOnClickListener {
            startActivity(Intent(requireContext(), GeminiActivity::class.java))

        }

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = UnifiedNotesAdapter(requireContext(), filteredNotes)
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
        binding.AllNotesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
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

    private fun setupSeeAllToggle() {
        val seeAllContainer = binding.root.findViewById<View>(R.id.seeAllContainer)

        seeAllContainer?.setOnClickListener {
            toggleNotesView()
        }

        binding.tvSeeAll.setOnClickListener {
            toggleNotesView()
        }

        binding.ivSeeAllArrow.setOnClickListener {
            toggleNotesView()
        }

        updateSeeAllUI()
    }

    private fun toggleNotesView() {
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
        val rotation = if (isExpanded) 180f else 0f
        binding.ivSeeAllArrow.animate()
            .rotation(rotation)
            .setDuration(200)
            .start()
    }

    private fun updateSeeAllUI() {
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
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

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

    private fun loadPinnedNotes() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        binding.progressBar.visibility = View.VISIBLE
        allNotes.clear()

        var loadedCollections = 0
        val totalCollections = 4

        fun checkIfAllLoaded() {
            loadedCollections++
            if (loadedCollections == totalCollections) {
                binding.progressBar.visibility = View.GONE
                sortAndUpdatePinnedNotes()
            }
        }

        db.collection("Journal")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("isPinned", true)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val journal = doc.toObject(Journal::class.java)
                    allNotes.add(UnifiedNoteItem.JournalItem(journal = journal, id = doc.id))
                }
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
                for (doc in documents) {
                    val textNote = doc.toObject(TextNote::class.java).copy(id = doc.id)
                    allNotes.add(UnifiedNoteItem.TextNoteItem(textNote))
                }
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
                for (doc in documents) {
                    val todoItem = doc.toObject(TodoItem::class.java).copy(id = doc.id)
                    allNotes.add(UnifiedNoteItem.TodoItemWrapper(todoItem))
                }
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
                for (doc in documents) {
                    val audioNote = doc.toObject(AudioNote::class.java).copy(id = doc.id)
                    allNotes.add(UnifiedNoteItem.AudioNoteItem(audioNote))
                }
                checkIfAllLoaded()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading pinned audio notes", e)
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

    private fun sortAndUpdatePinnedNotes() {
        val sortedNotes = if (sortAscending) {
            allNotes.sortedBy { it.timeAdded }
        } else {
            allNotes.sortedByDescending { it.timeAdded }
        }

        allNotes.clear()
        allNotes.addAll(sortedNotes)

        filterNotes(binding.edtTxtSrch.text.toString())
    }

    private fun updateEmptyState() {
        val emptyLayout = binding.root.findViewById<View>(R.id.emptyStateLayout)

        if (filteredNotes.isEmpty()) {
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
        val currentUser = firebaseAuth.currentUser

        if (currentUser == null) {
            binding.username.text = "Guest"
            return
        }

        val displayName = currentUser.displayName
        val email = currentUser.email

        val userName = when {
            !displayName.isNullOrEmpty() -> displayName
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
            startActivity(Intent(requireContext(), JournalListActivity::class.java))
        }

        binding.TodoCard.setOnClickListener {
            startActivity(Intent(requireContext(), TodoListActivity::class.java))
        }

        binding.audioCard.setOnClickListener {
            startActivity(Intent(requireContext(), AudioNotesListActivity::class.java))
        }

        binding.textCard.setOnClickListener {
            startActivity(Intent(requireContext(), TextNotesListActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()

        if (showOnlyPinned) {
            loadPinnedNotes()
        } else {
            loadAllNotes()
        }
    }

    override fun onPause() {
        super.onPause()
        adapter.releasePlayer()
    }
}