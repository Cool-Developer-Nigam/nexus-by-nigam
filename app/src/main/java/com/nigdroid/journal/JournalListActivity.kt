package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
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
import com.nigdroid.journal.databinding.ActivityJournalListBinding
import com.nigdroid.journal.databinding.DialogConfirmSignoutBinding

class JournalListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJournalListBinding
    private lateinit var toolbar: Toolbar

    //    Firebase references
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var user: FirebaseUser
    var db = FirebaseFirestore.getInstance()
    var collectionReference: CollectionReference = db.collection("Journal")

    lateinit var journalList: MutableList<Journal>
    lateinit var adapter: JournalRecyclerAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_journal_list)

        setupCustomToolbar()

//  Firebase authentification
        firebaseAuth = Firebase.auth
        user = firebaseAuth.currentUser!!

//  RecyclerView
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        binding.floatingActionButton.setOnClickListener {
            startActivity(Intent(this, AddJournalActivity::class.java))
        }

//        Post arrayList
        journalList = arrayListOf<Journal>()
    }

    private fun setupCustomToolbar() {
        toolbar = binding.toolbarLayout.toolbar
        setSupportActionBar(toolbar)

        // Hide default title
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.toolbarLayout.signoutBtn.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun showDeleteConfirmationDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)

        // Use data binding
        val dialogBinding = DialogConfirmSignoutBinding.inflate(layoutInflater)

        dialogBinding.btnDelete.setOnClickListener {
            firebaseAuth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
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
        loadJournals()
    }

    private fun loadJournals() {
        // Show progress bar, hide everything else
        binding.progressBar.visibility = View.VISIBLE
        binding.NoPostTv.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE

        // Clear the list before fetching to avoid duplicates
        journalList.clear()

        collectionReference
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                // Hide progress bar
                binding.progressBar.visibility = View.GONE

                if (!querySnapshot.isEmpty) {
                    // Save username to shared preferences
                    val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                    sharedPref.edit {
                        putString(
                            "username",
                            querySnapshot.documents[0].data?.get("username").toString()
                        )
                    }

                    // Add journals to list
                    for (document in querySnapshot) {
                        val journal = Journal(
                            document.data["title"].toString(),
                            document.data["thoughts"].toString(),
                            document.data["imageUrl"].toString(),
                            document.data["userId"].toString(),
                            document.data["timeAdded"].toString(),
                            document.data["username"].toString()
                        )
                        journalList.add(journal)
                    }

                    // Sort by timeAdded in descending order (newest first)
                    // Assuming timeAdded is stored as a timestamp string
                    journalList.sortByDescending { it.timeAdded }

                    // Set up adapter and show RecyclerView
                    adapter = JournalRecyclerAdapter(this, journalList)
                    binding.recyclerView.adapter = adapter
                    binding.recyclerView.visibility = View.VISIBLE

                } else {
                    // No journals found - show "No Posts" message
                    binding.NoPostTv.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { exception ->
                // Hide progress bar on error
                binding.progressBar.visibility = View.GONE

                Toast.makeText(
                    this,
                    "Error loading journals: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()

                // Show "No Posts" message on error
                binding.NoPostTv.visibility = View.VISIBLE
            }
    }

    // Call this method from the adapter when a journal is deleted
    fun onJournalDeleted() {
        if (journalList.isEmpty()) {
            binding.NoPostTv.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        }
    }
}