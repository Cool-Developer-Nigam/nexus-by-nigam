package com.nigdroid.journal

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.nigdroid.journal.databinding.ActivityTrashBinding

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding
    private lateinit var adapter: TrashNotesAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    companion object {
        private const val TAG = "TrashActivity"
        private const val COLLECTION_TRASH = "Trash"
        private const val DAYS_BEFORE_DELETE = 7
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadTrashedNotes()
        setupEmptyTrashButton()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Trash"
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        // Don't pass a list to the adapter - let it create its own
        adapter = TrashNotesAdapter(
            context = this,
            trashedItems = mutableListOf(),  // Empty list initially
            onRestoreClick = { item -> restoreItem(item) },
            onDeleteClick = { item -> permanentlyDeleteItem(item) }
        )

        binding.recyclerViewTrash.apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            adapter = this@TrashActivity.adapter
        }
    }

    private fun loadTrashedNotes() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "No user logged in")
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "=== Loading trash for user: $userId ===")
        binding.progressBar.visibility = View.VISIBLE

        db.collection(COLLECTION_TRASH)
            .whereEqualTo("userId", userId)
            .orderBy("trashedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                binding.progressBar.visibility = View.GONE

                if (error != null) {
                    Log.e(TAG, "Error loading trash: ${error.message}", error)
                    Toast.makeText(this, "Error loading trash: ${error.message}", Toast.LENGTH_SHORT).show()
                    updateEmptyState(0)
                    return@addSnapshotListener
                }

                Log.d(TAG, "Received ${snapshots?.size() ?: 0} documents from Trash collection")

                // Use a temporary list to avoid reference issues
                val loadedItems = mutableListOf<TrashedItem>()

                snapshots?.documents?.forEach { doc ->
                    try {
                        Log.d(TAG, "=== Processing document: ${doc.id} ===")

                        val docId = doc.id
                        val originalId = doc.getString("originalId") ?: ""
                        val userIdField = doc.getString("userId") ?: ""
                        val type = doc.getString("type") ?: ""
                        val trashedAt = doc.getLong("trashedAt") ?: 0L

                        val originalData = doc.get("originalData") as? Map<*, *>

                        if (originalData != null) {
                            val convertedData = originalData.entries.associate {
                                it.key.toString() to (it.value ?: "")
                            }

                            val item = TrashedItem(
                                id = docId,
                                originalId = originalId,
                                userId = userIdField,
                                type = type,
                                trashedAt = trashedAt,
                                originalData = convertedData
                            )

                            loadedItems.add(item)

                            val title = convertedData["title"] ?: "No title"
                            Log.d(TAG, "✓ Added item: $type - $title")
                        } else {
                            Log.e(TAG, "✗ originalData is null for document ${doc.id}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "✗ Error parsing document ${doc.id}: ${e.message}", e)
                    }
                }

                Log.d(TAG, "=== Total items loaded: ${loadedItems.size} ===")

                // Update adapter with the new list
                adapter.updateList(loadedItems)
                updateEmptyState(loadedItems.size)

                autoDeleteExpiredItems(loadedItems)
            }
    }

    private fun autoDeleteExpiredItems(items: List<TrashedItem>) {
        val currentTime = System.currentTimeMillis()
        val sevenDaysInMillis = DAYS_BEFORE_DELETE * 24 * 60 * 60 * 1000L

        val expiredItems = items.filter { item ->
            (currentTime - item.trashedAt) >= sevenDaysInMillis
        }

        if (expiredItems.isNotEmpty()) {
            Log.d(TAG, "Auto-deleting ${expiredItems.size} expired items")
            expiredItems.forEach { expiredItem ->
                permanentlyDeleteItem(expiredItem, silent = true)
            }
        }
    }

    private fun restoreItem(item: TrashedItem) {
        Log.d(TAG, "=== Restoring item: ${item.type} (ID: ${item.id}) ===")

        val originalCollection = when (item.type) {
            "journal" -> "Journal"
            "textNote" -> "TextNotes"
            "todo" -> "TodoItems"
            "audio" -> "AudioNotes"
            else -> {
                Toast.makeText(this, "Unknown item type", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val documentId = if (item.type == "journal") {
            db.collection(originalCollection).document().id
        } else {
            item.originalId
        }

        Log.d(TAG, "Restoring to collection: $originalCollection, document: $documentId")

        db.collection(originalCollection)
            .document(documentId)
            .set(item.originalData)
            .addOnSuccessListener {
                Log.d(TAG, "✓ Restored to original collection")

                db.collection(COLLECTION_TRASH)
                    .document(item.id)
                    .delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "✓ Removed from trash")
                        Toast.makeText(this, "Item restored", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "✗ Failed to remove from trash: ${e.message}", e)
                        Toast.makeText(this, "Restored but failed to remove from trash", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "✗ Failed to restore: ${e.message}", e)
                Toast.makeText(this, "Failed to restore: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun permanentlyDeleteItem(item: TrashedItem, silent: Boolean = false) {
        Log.d(TAG, "=== Permanently deleting item: ${item.id} ===")

        db.collection(COLLECTION_TRASH)
            .document(item.id)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "✓ Deleted from trash collection")
                deleteAssociatedFiles(item)

                if (!silent) {
                    Toast.makeText(this, "Deleted permanently", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "✗ Failed to delete: ${e.message}", e)
                if (!silent) {
                    Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun deleteAssociatedFiles(item: TrashedItem) {
        val fileUrl = when (item.type) {
            "journal" -> item.originalData["imageUrl"] as? String
            "audio" -> item.originalData["audioUrl"] as? String
            else -> null
        }

        fileUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            try {
                Log.d(TAG, "Deleting associated file: $url")
                val storageRef = storage.getReferenceFromUrl(url)
                storageRef.delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "✓ Deleted file from storage")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to delete file: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting file: ${e.message}", e)
            }
        }
    }

    private fun setupEmptyTrashButton() {
        binding.btnEmptyTrash.setOnClickListener {
            if (adapter.itemCount == 0) {
                Toast.makeText(this, "Trash is already empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Empty Trash?")
                .setMessage("All items in trash will be permanently deleted. This action cannot be undone.")
                .setPositiveButton("Empty Trash") { _, _ ->
                    emptyTrash()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun emptyTrash() {
        val userId = auth.currentUser?.uid ?: return

        Log.d(TAG, "=== Emptying trash for user: $userId ===")

        db.collection(COLLECTION_TRASH)
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Found ${documents.size()} items to delete")

                val batch = db.batch()

                documents.forEach { doc ->
                    batch.delete(doc.reference)

                    try {
                        val originalData = doc.get("originalData") as? Map<*, *>
                        if (originalData != null) {
                            val convertedData = originalData.entries.associate {
                                it.key.toString() to (it.value ?: "")
                            }

                            val item = TrashedItem(
                                id = doc.id,
                                originalId = doc.getString("originalId") ?: "",
                                userId = doc.getString("userId") ?: "",
                                type = doc.getString("type") ?: "",
                                trashedAt = doc.getLong("trashedAt") ?: 0L,
                                originalData = convertedData
                            )
                            deleteAssociatedFiles(item)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing item for file deletion: ${e.message}")
                    }
                }

                batch.commit()
                    .addOnSuccessListener {
                        Log.d(TAG, "✓ Trash emptied successfully")
                        Toast.makeText(this, "Trash emptied", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "✗ Failed to empty trash: ${e.message}", e)
                        Toast.makeText(this, "Failed to empty trash: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "✗ Failed to query trash items: ${e.message}", e)
                Toast.makeText(this, "Failed to empty trash: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateEmptyState(itemCount: Int) {
        Log.d(TAG, "Updating empty state. Items count: $itemCount")

        if (itemCount == 0) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewTrash.visibility = View.GONE
            binding.btnEmptyTrash.isEnabled = false
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerViewTrash.visibility = View.VISIBLE
            binding.btnEmptyTrash.isEnabled = true
        }
    }
}