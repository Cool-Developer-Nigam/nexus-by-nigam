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
import android.widget.PopupMenu
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
    private var sortAscending = false

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
        loadAllNotes()
        setupLayoutToggle()
        setupSearchFunctionality()

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
    }

    private fun setLinearLayout() {
        binding.AllNotesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        isStaggeredLayout = false
    }

    private fun setupLayoutToggle() {
        binding.btnLayoutToggle.setOnClickListener {
            showLayoutMenu(it)
        }
    }

    private fun showLayoutMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.layout_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_staggered -> {
                    setStaggeredLayout()
                    adapter.notifyDataSetChanged()
                    true
                }
                R.id.menu_linear -> {
                    setLinearLayout()
                    adapter.notifyDataSetChanged()
                    true
                }
                R.id.menu_sort_newest -> {
                    sortAscending = false
                    sortAndUpdateNotes()
                    true
                }
                R.id.menu_sort_oldest -> {
                    sortAscending = true
                    sortAndUpdateNotes()
                    true
                }
                else -> false
            }
        }
        popup.show()
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
            // If search is empty, show all sorted notes
            filteredNotes.addAll(allNotes)
        } else {
            // Filter notes based on search query (case-insensitive)
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

        // Load Todos
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

        // Update filtered notes with new sorted data
        filterNotes(binding.edtTxtSrch.text.toString())
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
        loadAllNotes()
    }

    override fun onPause() {
        super.onPause()
        adapter.releasePlayer()
    }
}