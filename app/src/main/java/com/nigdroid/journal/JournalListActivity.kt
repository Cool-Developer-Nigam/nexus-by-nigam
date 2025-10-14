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
    private lateinit var toolbar: Toolbar

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var user: FirebaseUser
    private var db = FirebaseFirestore.getInstance()
    private var collectionReference: CollectionReference = db.collection("Journal")

    private val pinnedJournals = mutableListOf<Journal>()
    private val unpinnedJournals = mutableListOf<Journal>()

    private lateinit var pinnedAdapter: JournalRecyclerAdapter
    private lateinit var unpinnedAdapter: JournalRecyclerAdapter
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

        firebaseAuth = Firebase.auth
        user = firebaseAuth.currentUser!!

        setupRecyclerViews()

        binding.floatingActionButton.setOnClickListener {
            startActivity(Intent(this, AddJournalActivity::class.java))
        }
    }

    private fun setupCustomToolbar() {
        toolbar = binding.toolbarLayout.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.toolbarLayout.signout.setOnClickListener {
            showDeleteConfirmationDialog()
        }
        binding.toolbarLayout.backBtn.setOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerViews() {
        // Pinned journals - check if pinnedRecyclerView exists
        binding.pinnedRecyclerView?.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        pinnedAdapter = JournalRecyclerAdapter(this, pinnedJournals)
        binding.pinnedRecyclerView?.adapter = pinnedAdapter

        // Unpinned journals
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        unpinnedAdapter = JournalRecyclerAdapter(this, unpinnedJournals)
        binding.recyclerView.adapter = unpinnedAdapter
    }

    private fun showDeleteConfirmationDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)
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
        listenerRegistration?.remove()
    }

    private fun loadJournalsWithOfflineSupport() {
        binding.progressBar.visibility = View.VISIBLE
        binding.NoPostTv.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.pinnedSection?.visibility = View.GONE

        pinnedJournals.clear()
        unpinnedJournals.clear()

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
                    processJournals(querySnapshot.documents.mapNotNull { document ->
                        try {
                            document.toObject(Journal::class.java)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing journal from cache", e)
                            null
                        }
                    })

                    saveUsername(querySnapshot.documents[0].data?.get("username").toString())
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

                    saveUsername(querySnapshot.documents[0].data?.get("username").toString())

                    val journals = querySnapshot.documents.mapNotNull { document ->
                        try {
                            document.toObject(Journal::class.java)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing journal", e)
                            null
                        }
                    }

                    processJournals(journals)
                } else {
                    pinnedJournals.clear()
                    unpinnedJournals.clear()
                    binding.NoPostTv.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                    binding.pinnedSection?.visibility = View.GONE

                    if (querySnapshot?.metadata?.isFromCache == true) {
                        Log.d(TAG, "No cached journals found")
                    } else {
                        Log.d(TAG, "No journals found on server")
                    }
                }
            }
    }

    private fun processJournals(journals: List<Journal>) {
        pinnedJournals.clear()
        unpinnedJournals.clear()

        for (journal in journals) {
            if (journal.isPinned) {
                pinnedJournals.add(journal)
            } else {
                unpinnedJournals.add(journal)
            }
        }

        updateUI()
    }

    private fun updateUI() {
        // Pinned section
        if (pinnedJournals.isEmpty()) {
            binding.pinnedSection?.visibility = View.GONE
        } else {
            binding.pinnedSection?.visibility = View.VISIBLE
            pinnedAdapter.notifyDataSetChanged()
        }

        // Unpinned section
        if (unpinnedJournals.isEmpty() && pinnedJournals.isEmpty()) {
            binding.NoPostTv.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.othersSection?.visibility = View.GONE
        } else if (unpinnedJournals.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.othersSection?.visibility = View.GONE
            binding.NoPostTv.visibility = View.GONE
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.othersSection?.visibility = View.VISIBLE
            binding.NoPostTv.visibility = View.GONE

            if (pinnedJournals.isEmpty()) {
                binding.othersSectionTitle?.visibility = View.GONE
            } else {
                binding.othersSectionTitle?.visibility = View.VISIBLE
            }

            unpinnedAdapter.notifyDataSetChanged()
        }
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

        if (pinnedJournals.isEmpty() && unpinnedJournals.isEmpty()) {
            binding.NoPostTv.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.pinnedSection?.visibility = View.GONE
        }
    }

    fun onJournalDeleted() {
        if (pinnedJournals.isEmpty() && unpinnedJournals.isEmpty()) {
            binding.NoPostTv.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.pinnedSection?.visibility = View.GONE
        }
    }
}