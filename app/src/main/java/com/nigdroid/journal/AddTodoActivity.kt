package com.nigdroid.journal

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nigdroid.journal.databinding.ActivityAddTodoBinding

class AddTodoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTodoBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private val checklistItems = mutableListOf<ChecklistItem>()
    private lateinit var checklistAdapter: ChecklistEditAdapter

    private var todoId: String? = null
    private var isPinned = false
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_todo)

        firebaseAuth = FirebaseAuth.getInstance()

        // Check if editing existing todo
        todoId = intent.getStringExtra("TODO_ID")
        isEditMode = todoId != null

        // Setup UI
        setupToolbar()
        setupRecyclerView()
        setupClickListeners()

        // Load existing todo if in edit mode
        if (isEditMode) {
            loadTodoData()
        } else {
            // Start with one empty item
            addNewItem()
        }
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnPin.setOnClickListener {
            isPinned = !isPinned
            updatePinIcon()
        }

        // Set initial pin state
        isPinned = intent.getBooleanExtra("IS_PINNED", false)
        updatePinIcon()
    }

    private fun updatePinIcon() {
        if (isPinned) {
            binding.btnPin.setImageResource(R.drawable.ic_pin)
        } else {
            binding.btnPin.setImageResource(R.drawable.ic_pin_outline)
        }
    }

    private fun setupRecyclerView() {
        binding.checklistRecyclerView.layoutManager = LinearLayoutManager(this)
        checklistAdapter = ChecklistEditAdapter(
            this,
            checklistItems,
            onItemChanged = { position, item ->
                // Auto-save or update logic here if needed
            },
            onDeleteItem = { position ->
                if (checklistItems.size > 1) {
                    checklistItems.removeAt(position)
                    checklistAdapter.notifyItemRemoved(position)
                }
            }
        )
        binding.checklistRecyclerView.adapter = checklistAdapter
    }

    private fun setupClickListeners() {
        binding.btnAddItem.setOnClickListener {
            addNewItem()
        }

        binding.btnSave.setOnClickListener {
            saveTodo()
        }

        binding.btnDelete.setOnClickListener {
            if (isEditMode) {
                showDeleteConfirmationDialog()
            } else {
                finish()
            }
        }
    }

    private fun addNewItem() {
        val newItem = ChecklistItem(text = "", isChecked = false)
        checklistItems.add(newItem)
        checklistAdapter.notifyItemInserted(checklistItems.size - 1)

        // Scroll to the new item
        binding.checklistRecyclerView.smoothScrollToPosition(checklistItems.size - 1)
    }

    private fun loadTodoData() {
        todoId?.let { id ->
            db.collection("TodoItems").document(id).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val todo = document.toObject(TodoItem::class.java)
                        todo?.let {
                            binding.etTitle.setText(it.title)
                            isPinned = it.isPinned
                            updatePinIcon()

                            checklistItems.clear()
                            checklistItems.addAll(it.items)
                            checklistAdapter.notifyDataSetChanged()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading todo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveTodo() {
        val title = binding.etTitle.text.toString().trim()

        // Remove empty items before saving
        val filteredItems = checklistItems.filter { it.text.trim().isNotEmpty() }

        if (title.isEmpty() && filteredItems.isEmpty()) {
            Toast.makeText(this, "Please add a title or at least one item", Toast.LENGTH_SHORT).show()
            return
        }

        val user = firebaseAuth.currentUser ?: return
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val username = sharedPref.getString("username", "Anonymous") ?: "Anonymous"

        val currentTime = System.currentTimeMillis()

        val todo = TodoItem(
            id = todoId ?: "",
            title = title,
            items = filteredItems.toMutableList(),
            userId = user.uid,
            username = username,
            timeAdded = if (isEditMode) intent.getLongExtra("TIME_ADDED", currentTime) else currentTime,
            timeModified = currentTime,
            isPinned = isPinned
        )

        if (isEditMode && todoId != null) {
            // Update existing todo
            db.collection("TodoItems").document(todoId!!)
                .set(todo)
                .addOnSuccessListener {
                    Toast.makeText(this, "Todo updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error updating todo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Create new todo
            db.collection("TodoItems")
                .add(todo)
                .addOnSuccessListener {
                    Toast.makeText(this, "Todo created successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error creating todo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete To-Do List")
            .setMessage("Are you sure you want to delete this to-do list?")
            .setPositiveButton("Delete") { _, _ ->
                deleteTodo()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTodo() {
        todoId?.let { id ->
            db.collection("TodoItems").document(id)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Todo deleted successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error deleting todo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}