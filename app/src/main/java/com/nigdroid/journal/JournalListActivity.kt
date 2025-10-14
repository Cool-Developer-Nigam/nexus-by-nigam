package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
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
    private lateinit var toolbar: Toolbar

    // Firebase references
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var user: FirebaseUser
    private var db = FirebaseFirestore.getInstance()
    private var collectionReference: CollectionReference = db.collection("Journal")

    private lateinit var journalList: MutableList<Journal>
    private lateinit var adapter: JournalRecyclerAdapter
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

        setupCustomToolbar()

        // Firebase authentication
        firebaseAuth = Firebase.auth
        user = firebaseAuth.currentUser!!

        // RecyclerView
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        binding.floatingActionButton.setOnClickListener {
            startActivity(Intent(this, AddJournalActivity::class.java))
        }

        // Post arrayList
        journalList = arrayListOf<Journal>()
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

    override fun onStart() {
        super.onStart()
        loadJournalsWithOfflineSupport()
    }

    override fun onStop() {
        super.onStop()
        // Remove real-time listener when activity stops
        listenerRegistration?.remove()
    }

    /**
     * Loads journals with offline-first strategy
     * 1. First tries to load from cache for instant display
     * 2. Then fetches from server and sets up real-time listener
     */
    private fun loadJournalsWithOfflineSupport() {
        // Show progress bar, hide everything else
        binding.progressBar.visibility = View.VISIBLE
        binding.NoPostTv.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE

        // Clear the list before fetching to avoid duplicates
        journalList.clear()

        // Try cache first for instant load
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
                    displayJournals(querySnapshot.documents.mapNotNull { document ->
                        try {
                            Journal(
                                document.data?.get("title").toString(),
                                document.data?.get("thoughts").toString(),
                                document.data?.get("imageUrl").toString(),
                                document.data?.get("userId").toString(),
                                document.data?.get("timeAdded").toString(),
                                document.data?.get("username").toString()
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing journal from cache", e)
                            null
                        }
                    })

                    // Save username to shared preferences
                    saveUsername(querySnapshot.documents[0].data?.get("username").toString())
                }

                // Now fetch from server to update
                setupRealtimeListener()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Cache load failed, loading from server", e)
                // Cache failed or empty, load from server
                setupRealtimeListener()
            }
    }

    /**
     * Sets up real-time listener for automatic updates
     * This will keep data in sync when online
     */
    private fun setupRealtimeListener() {
        listenerRegistration = collectionReference
            .whereEqualTo("userId", user.uid)
            .orderBy("timeAdded", Query.Direction.DESCENDING)
            .addSnapshotListener { querySnapshot, error ->
                // Hide progress bar
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

                    // Save username to shared preferences
                    saveUsername(querySnapshot.documents[0].data?.get("username").toString())

                    // Clear and rebuild list
                    val newJournals = querySnapshot.documents.mapNotNull { document ->
                        try {
                            Journal(
                                document.data?.get("title").toString(),
                                document.data?.get("thoughts").toString(),
                                document.data?.get("imageUrl").toString(),
                                document.data?.get("userId").toString(),
                                document.data?.get("timeAdded").toString(),
                                document.data?.get("username").toString()
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing journal", e)
                            null
                        }
                    }

                    displayJournals(newJournals)

                } else {
                    // No journals found
                    journalList.clear()
                    binding.NoPostTv.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE

                    if (querySnapshot?.metadata?.isFromCache == true) {
                        Log.d(TAG, "No cached journals found")
                    } else {
                        Log.d(TAG, "No journals found on server")
                    }
                }
            }
    }

    private fun displayJournals(journals: List<Journal>) {
        journalList.clear()
        journalList.addAll(journals)

        // Set up adapter if not already set
        if (!::adapter.isInitialized) {
            adapter = JournalRecyclerAdapter(this, journalList)
            binding.recyclerView.adapter = adapter
        } else {
            adapter.notifyDataSetChanged()
        }

        binding.recyclerView.visibility = View.VISIBLE
        binding.NoPostTv.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun saveUsername(username: String) {
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPref.edit {
            putString(KEY_USERNAME, username)
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

        // Show "No Posts" message on error if list is empty
        if (journalList.isEmpty()) {
            binding.NoPostTv.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        }
    }

    /**
     * Called from adapter when a journal is deleted
     */
    fun onJournalDeleted() {
        if (journalList.isEmpty()) {
            binding.NoPostTv.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        }
    }

    /**
     * Manual refresh method (can be called from pull-to-refresh)
     */
    private fun refreshJournals() {
        collectionReference
            .whereEqualTo("userId", user.uid)
            .orderBy("timeAdded", Query.Direction.DESCENDING)
            .get(Source.SERVER)
            .addOnSuccessListener { querySnapshot ->
                Log.d(TAG, "Manual refresh completed")
                // Real-time listener will handle the update
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Manual refresh failed", exception)
                Toast.makeText(
                    this,
                    "Failed to refresh: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}