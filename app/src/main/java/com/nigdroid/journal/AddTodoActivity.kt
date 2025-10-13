package com.nigdroid.journal

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nigdroid.journal.databinding.ActivityAddTodoBinding
import com.nigdroid.journal.databinding.DialogConfirmDeleteBinding

class AddTodoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTodoBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private val checklistItems = mutableListOf<ChecklistItem>()
    private lateinit var checklistAdapter: ChecklistEditAdapter

    private var todoId: String? = null
    private var isPinned = false
    private var isEditMode = false
    private var timeAdded: Long = 0L  // Add this to preserve original creation time

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_todo)

        firebaseAuth = FirebaseAuth.getInstance()

        // Check if editing existing todo
        todoId = intent.getStringExtra("TODO_ID")
        isEditMode = todoId != null

        // Get timeAdded from intent if editing
        timeAdded = intent.getLongExtra("TIME_ADDED", System.currentTimeMillis())

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

        // Set initial pin state from intent
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
                            timeAdded = it.timeAdded  // Preserve original creation time
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

        if (isEditMode && todoId != null) {
            // Update existing todo - preserve the document ID and timeAdded
            val todo = TodoItem(
                id = todoId!!,
                title = title,
                items = filteredItems.toMutableList(),
                userId = user.uid,
                username = username,
                timeAdded = timeAdded,  // Use preserved timeAdded
                timeModified = currentTime,
                isPinned = isPinned
            )

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
            // Create new todo - get the document reference first to set the ID
            val docRef = db.collection("TodoItems").document()

            val todo = TodoItem(
                id = docRef.id,  // Set the ID from the document reference
                title = title,
                items = filteredItems.toMutableList(),
                userId = user.uid,
                username = username,
                timeAdded = currentTime,
                timeModified = currentTime,
                isPinned = isPinned
            )

            docRef.set(todo)
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
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)

        // Use data binding
        val dialogBinding = DialogConfirmDeleteBinding.inflate(LayoutInflater.from(this))

        dialogBinding.btnDelete.setOnClickListener {
            deleteTodo()
            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
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