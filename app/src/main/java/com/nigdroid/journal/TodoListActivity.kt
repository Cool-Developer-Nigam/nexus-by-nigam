package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nigdroid.journal.databinding.ActivityTodoListBinding
import com.nigdroid.journal.databinding.DialogConfirmSignoutBinding

class TodoListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTodoListBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var user: FirebaseUser
    private val db = FirebaseFirestore.getInstance()

    private lateinit var toolbar: Toolbar
    private lateinit var collectionReference: CollectionReference

    private val pinnedTodos = mutableListOf<UnifiedNoteItem>()
    private val unpinnedTodos = mutableListOf<UnifiedNoteItem>()

    private lateinit var pinnedAdapter: UnifiedNotesAdapter
    private lateinit var unpinnedAdapter: UnifiedNotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_todo_list)

        // Setup toolbar
        setupCustomToolbar()

        // Firebase auth
        firebaseAuth = FirebaseAuth.getInstance()
        user = firebaseAuth.currentUser!!
        collectionReference = db.collection("TodoItems")

        // Setup RecyclerViews with staggered grid (2 columns like Google Keep)
        setupRecyclerViews()

        // FAB click listener
        binding.fabAddTodo.setOnClickListener {
            startActivity(Intent(this, AddTodoActivity::class.java))
        }
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

    private fun setupRecyclerViews() {
        // Pinned todos
        binding.pinnedRecyclerView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        pinnedAdapter = UnifiedNotesAdapter(this, pinnedTodos)
        binding.pinnedRecyclerView.adapter = pinnedAdapter

        // Unpinned todos
        binding.todosRecyclerView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        unpinnedAdapter = UnifiedNotesAdapter(this, unpinnedTodos)
        binding.todosRecyclerView.adapter = unpinnedAdapter
    }

    override fun onStart() {
        super.onStart()
        loadTodos()
    }

    override fun onStop() {
        super.onStop()
        // Release media player resources
        pinnedAdapter.releasePlayer()
        unpinnedAdapter.releasePlayer()
    }

    private fun loadTodos() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE

        collectionReference
            .whereEqualTo("userId", user.uid)
            .orderBy("timeModified", Query.Direction.DESCENDING)
            .addSnapshotListener { querySnapshot, error ->
                binding.progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    pinnedTodos.clear()
                    unpinnedTodos.clear()

                    for (document in querySnapshot) {
                        val todo = document.toObject(TodoItem::class.java).copy(
                            id = document.id
                        )

                        val todoItem = UnifiedNoteItem.TodoItemWrapper(todo)

                        if (todo.isPinned) {
                            pinnedTodos.add(todoItem)
                        } else {
                            unpinnedTodos.add(todoItem)
                        }
                    }

                    updateUI()
                } else {
                    pinnedTodos.clear()
                    unpinnedTodos.clear()
                    updateUI()
                }
            }
    }

    private fun updateUI() {
        // Pinned section
        if (pinnedTodos.isEmpty()) {
            binding.pinnedSection.visibility = View.GONE
        } else {
            binding.pinnedSection.visibility = View.VISIBLE
            pinnedAdapter.notifyDataSetChanged()
        }

        // Unpinned section
        if (unpinnedTodos.isEmpty() && pinnedTodos.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.othersSection.visibility = View.GONE
        } else if (unpinnedTodos.isEmpty()) {
            binding.othersSection.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.GONE
        } else {
            binding.othersSection.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE

            // Hide "OTHERS" label if no pinned items
            if (pinnedTodos.isEmpty()) {
                binding.othersSectionTitle.visibility = View.GONE
            } else {
                binding.othersSectionTitle.visibility = View.VISIBLE
            }

            unpinnedAdapter.notifyDataSetChanged()
        }
    }
}