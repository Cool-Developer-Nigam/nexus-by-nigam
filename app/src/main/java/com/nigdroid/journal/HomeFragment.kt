package com.nigdroid.journal

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nigdroid.journal.databinding.FragmentHomeBinding
import kotlin.random.Random

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: UnifiedNotesAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val allNotes = mutableListOf<UnifiedNoteItem>()
    private var isStaggeredLayout = true
    private var sortAscending = false
    private var showOnlyPinned = true
    private var isExpanded = false
    private var isFragmentAlive = false

    private val TAG = "HomeFragment"

    // 20 different gradient colors
    private val bannerGradients = listOf(
        intArrayOf(0xFFFF6B6B.toInt(), 0xFFEE5A6F.toInt()),
        intArrayOf(0xFF4ECDC4.toInt(), 0xFF44A08D.toInt()),
        intArrayOf(0xFF556270.toInt(), 0xFFFF6B6B.toInt()),
        intArrayOf(0xFF00B4DB.toInt(), 0xFF0083B0.toInt()),
        intArrayOf(0xFFFC466B.toInt(), 0xFF3F5EFB.toInt()),
        intArrayOf(0xFF11998E.toInt(), 0xFF38EF7D.toInt()),
        intArrayOf(0xFFFF758C.toInt(), 0xFFFF7EB3.toInt()),
        intArrayOf(0xFF667EEA.toInt(), 0xFF764BA2.toInt()),
        intArrayOf(0xFFF093FB.toInt(), 0xFFF5576C.toInt()),
        intArrayOf(0xFF4FACFE.toInt(), 0xFF00F2FE.toInt()),
        intArrayOf(0xFF43E97B.toInt(), 0xFF38F9D7.toInt()),
        intArrayOf(0xFFFA709A.toInt(), 0xFFFEE140.toInt()),
        intArrayOf(0xFF30CFD0.toInt(), 0xFF330867.toInt()),
        intArrayOf(0xFFA8EDEA.toInt(), 0xFFFED6E3.toInt()),
        intArrayOf(0xFFFF9A9E.toInt(), 0xFFFAD0C4.toInt()),
        intArrayOf(0xFFFBC2EB.toInt(), 0xFFA6C1EE.toInt()),
        intArrayOf(0xFFFFDEE9.toInt(), 0xFFB5FFFC.toInt()),
        intArrayOf(0xFFFEAC5E.toInt(), 0xFFC779D0.toInt()),
        intArrayOf(0xFF4BC0C8.toInt(), 0xFFC779D0.toInt()),
        intArrayOf(0xFFD299C2.toInt(), 0xFFFEF9D7.toInt())
    )

    // 20 different banner texts
    private val bannerTexts = listOf(
        "Discover More Features\nTap to Explore",
        "Unlock Your Creativity\nStart Journaling Today",
        "Your Thoughts Matter\nCapture Every Moment",
        "Stay Organized\nCreate Your First Note",
        "Begin Your Journey\nWrite Your Story",
        "Express Yourself\nStart Creating Now",
        "Keep Track of Ideas\nTap to Get Started",
        "Organize Your Life\nOne Note at a Time",
        "Capture Inspiration\nCreate Something Amazing",
        "Your Digital Diary\nBegins Here",
        "Transform Thoughts\nInto Actions",
        "Never Forget Again\nStart Noting Down",
        "Build Better Habits\nOne Task at a Time",
        "Your Personal Space\nCreate & Organize",
        "Ideas Worth Keeping\nTap to Start",
        "Document Your Day\nBegin Your Story",
        "Stay Productive\nCreate Your List",
        "Memories Matter\nStart Recording",
        "Achieve Your Goals\nPlan & Execute",
        "Your Creative Hub\nExplore Features"
    )

    // Colors for logo rotation animation
    private val logoColors = listOf(
        0xFFFF6B6B.toInt(),
        0xFF4ECDC4.toInt(),
        0xFFFC466B.toInt(),
        0xFF11998E.toInt(),
        0xFF667EEA.toInt(),
        0xFF4FACFE.toInt(),
        0xFFFA709A.toInt(),
        0xFF30CFD0.toInt()
    )

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

        // Set random banner style on initial load
        updateBannerStyle()

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

    private fun updateBannerStyle() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        val randomIndex = Random.nextInt(bannerGradients.size)
        val colors = bannerGradients[randomIndex]
        val text = bannerTexts[randomIndex]

        // Create gradient drawable
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            colors
        )
        gradientDrawable.cornerRadius = 48f // 16dp * 3 for pixel density

        // Find the LinearLayout inside banner CardView
        val bannerLayout = binding.banner.getChildAt(0) as? android.widget.LinearLayout
        bannerLayout?.background = gradientDrawable

        // Update text
        val bannerTextView = bannerLayout?.getChildAt(0) as? TextView
        bannerTextView?.text = text
    }

    private fun rotateLogoWithColorChange() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        try {
            // Find the logo view
            val logoView = binding.root.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.app_logo)

            if (logoView != null) {
                // Get random color
                val randomColor = logoColors[Random.nextInt(logoColors.size)]

                // Apply color tint to logo
                logoView.setColorFilter(randomColor)

                // Create translate (upward) animation
                val translateAnimation = android.view.animation.TranslateAnimation(
                    0f, 0f,  // No horizontal movement
                    0f, -200f  // Move up by 200 pixels
                ).apply {
                    duration = 600
                    fillAfter = false
                }

                // Create rotation animation
                val rotateAnimation = android.view.animation.RotateAnimation(
                    0f, 360f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 1000
                    startOffset = 200  // Start after translate begins
                    fillAfter = false
                }

                // Create scale animation for bounce effect
                val scaleAnimation = android.view.animation.ScaleAnimation(
                    1f, 1.2f,  // Scale from 100% to 120%
                    1f, 1.2f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 300
                    startOffset = 200
                    repeatMode = android.view.animation.Animation.REVERSE
                    repeatCount = 1
                    fillAfter = false
                }

                // Combine all animations
                val animationSet = android.view.animation.AnimationSet(false).apply {
                    addAnimation(translateAnimation)
                    addAnimation(rotateAnimation)
                    addAnimation(scaleAnimation)
                }

                // Set animation listener to clear color after completion
                animationSet.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                    override fun onAnimationStart(animation: android.view.animation.Animation?) {}

                    override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                        if (isAdded && isFragmentAlive && _binding != null) {
                            logoView.clearColorFilter()
                        }
                    }

                    override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
                })

                // Start animation
                logoView.startAnimation(animationSet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating logo", e)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout = binding.swipeRefreshLayout

        swipeRefreshLayout.setColorSchemeResources(
            R.color.purple_500,
            R.color.teal_200,
            R.color.purple_700
        )

        swipeRefreshLayout.setOnRefreshListener {
            if (isAdded && isFragmentAlive) {
                // Rotate logo with color change on refresh
                rotateLogoWithColorChange()

                // Update banner style on refresh
                updateBannerStyle()

                refreshData()
            }
        }
    }

    private fun refreshData() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        loadUserProfile()

        if (showOnlyPinned) {
            loadPinnedNotes()
        } else {
            loadAllNotes()
        }

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
        updateSortIcon()

        binding.btnSort.setOnClickListener {
            if (isAdded && isFragmentAlive) {
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
            binding.tvFavouritesTitle.text = "Favourites"
        } else {
            binding.tvSeeAll.text = "Show less"
            binding.ivSeeAllArrow.rotation = 180f
            binding.tvFavouritesTitle.text = "All Notes"
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

        val sorted = if (showOnlyPinned) {
            if (sortAscending) {
                filtered.sortedBy { it.timeAdded }
            } else {
                filtered.sortedByDescending { it.timeAdded }
            }
        } else {
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
                    updateBannerVisibility()
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

                    if (allNotes.isEmpty()) {
                        Log.d(TAG, "No pinned notes found, loading all notes")
                        loadAllNotesAfterEmptyPinned()
                    } else {
                        Log.d(TAG, "Pinned notes loaded: ${allNotes.size} items")
                        applyCurrentFiltersAndSort()
                        updateBannerVisibility()
                    }
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

    private fun loadAllNotesAfterEmptyPinned() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        showOnlyPinned = false
        isExpanded = true
        updateSeeAllUI()

        loadAllNotes()
    }

    private fun updateBannerVisibility() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        binding.banner.visibility = View.VISIBLE
    }

    private fun updateEmptyState(isEmpty: Boolean = allNotes.isEmpty()) {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        val emptyLayout = binding.root.findViewById<View>(R.id.emptyStateLayout)

        if (isEmpty) {
            // Show banner and empty state when no notes
            binding.banner.visibility = View.VISIBLE
            emptyLayout?.visibility = View.VISIBLE
            binding.AllNotesRecyclerView.visibility = View.GONE

            val emptyMessage = binding.root.findViewById<TextView>(R.id.emptyStateMessage)
            emptyMessage?.text = if (showOnlyPinned) {
                "No pinned notes yet\nPin your important notes to see them here"
            } else {
                "No notes yet\nStart creating your first note"
            }
        } else {
            // Keep banner visible, hide empty state, show RecyclerView
            binding.banner.visibility = View.VISIBLE  // ✅ Fixed: Banner stays visible
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

        // Changed: Banner now opens VideoActivity instead of JournalListActivity
        binding.banner.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                val intent = Intent(requireContext(), VideoActivity::class.java)

                intent.putExtra("VIDEO_URI", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")

                // Use drawable resource
                val thumbnailUri = "android.resource://${requireContext().packageName}/${R.drawable.video_thumb}"
                intent.putExtra("THUMBNAIL_URL", thumbnailUri)

                intent.putExtra("VIDEO_TITLE", "App Tutorial")

                startActivity(intent)
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