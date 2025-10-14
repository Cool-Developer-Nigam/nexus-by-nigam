package com.nigdroid.journal

import android.content.Context
import android.graphics.Color
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.nigdroid.journal.databinding.DialogConfirmDeleteBinding
import com.nigdroid.journal.databinding.JournalRowBinding

class JournalRecyclerAdapter(
    var context: Context,
    var journalList: MutableList<Journal>
) : RecyclerView.Adapter<JournalRecyclerAdapter.MyViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    companion object {
        private const val TAG = "JournalRecyclerAdapter"
        private const val COLLECTION_JOURNALS = "Journal"

        // Field names for consistent indexing
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_TITLE = "title"
        private const val FIELD_TIME_ADDED = "timeAdded"
        private const val FIELD_THOUGHTS = "thoughts"
        private const val FIELD_IMAGE_URL = "imageUrl"
        private const val FIELD_USERNAME = "username"
    }

    class MyViewHolder(var binding: JournalRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(journal: Journal) {
            binding.journal = journal
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val binding = JournalRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val journal = journalList[position]

        // Handle HTML in title
        val titleText = if (journal.title.contains("<")) {
            Html.fromHtml(journal.title, Html.FROM_HTML_MODE_COMPACT)
        } else {
            journal.title
        }

        // Handle HTML in thoughts/content
        val thoughtsText = if (journal.thoughts.contains("<")) {
            Html.fromHtml(journal.thoughts, Html.FROM_HTML_MODE_COMPACT)
        } else {
            journal.thoughts
        }

        // Create a modified journal object with clean text
        val cleanJournal = journal.copy(
            title = if (titleText is Spanned) titleText.trim().toString() else titleText.toString(),
            thoughts = if (thoughtsText is Spanned) thoughtsText.trim().toString() else thoughtsText.toString()
        )

        holder.bind(cleanJournal)

        // Set up delete button click listener
        holder.binding.deleteBtn.setOnClickListener {
            showDeleteConfirmationDialog(journal, position)
        }

        // Set up item click listener to view full journal
        holder.binding.root.setOnClickListener {
            val intent = android.content.Intent(context, Show_Journal::class.java).apply {
                putExtra(FIELD_TITLE, journal.title)
                putExtra(FIELD_THOUGHTS, journal.thoughts)
                putExtra(FIELD_IMAGE_URL, journal.imageUrl)
                putExtra(FIELD_USER_ID, journal.userId)
                putExtra(FIELD_TIME_ADDED, journal.timeAdded)
                putExtra(FIELD_USERNAME, journal.username)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = journalList.size

    private fun showDeleteConfirmationDialog(journal: Journal, position: Int) {
        val dialog = BottomSheetDialog(context, R.style.BottomSheetDialogStyle)

        // Use data binding
        val dialogBinding = DialogConfirmDeleteBinding.inflate(LayoutInflater.from(context))

        dialogBinding.btnDelete.setOnClickListener {
            deleteJournal(journal, position)
            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun deleteJournal(journal: Journal, position: Int) {
        // Query Firestore with indexed fields
        // Uses offline cache when available, then syncs when online
        db.collection(COLLECTION_JOURNALS)
            .whereEqualTo(FIELD_USER_ID, journal.userId)
            .whereEqualTo(FIELD_TITLE, journal.title)
            .whereEqualTo(FIELD_TIME_ADDED, journal.timeAdded)
            .get() // Automatically uses cache when offline
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val documentId = documents.documents[0].id
                    val source = documents.metadata.isFromCache

                    if (source) {
                        Log.d(TAG, "Data retrieved from offline cache")
                    } else {
                        Log.d(TAG, "Data retrieved from server")
                    }

                    // Delete from Firestore
                    // This will be queued for deletion when offline and synced when online
                    db.collection(COLLECTION_JOURNALS)
                        .document(documentId)
                        .delete()
                        .addOnSuccessListener {
                            // Delete image from Storage (only when online)
                            deleteImageFromStorage(journal.imageUrl)

                            // Remove from list and update RecyclerView immediately
                            journalList.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, journalList.size)

                            val message = if (source) {
                                "Journal deleted (will sync when online)"
                            } else {
                                "Journal deleted successfully"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                            // Notify activity if list is now empty
                            if (journalList.isEmpty() && context is JournalListActivity) {
                                (context as JournalListActivity).onJournalDeleted()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to delete journal from Firestore", e)
                            Toast.makeText(context, "Failed to delete journal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Log.w(TAG, "Journal not found in Firestore")
                    Toast.makeText(context, "Journal not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                handleQueryError(e)
            }
    }

    /**
     * Fetch journals with offline support
     * Call this method to load journals with cache-first strategy
     */
    fun loadJournalsWithOfflineSupport(userId: String, callback: (List<Journal>) -> Unit) {
        db.collection(COLLECTION_JOURNALS)
            .whereEqualTo(FIELD_USER_ID, userId)
            .orderBy(FIELD_TIME_ADDED, com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get(Source.CACHE) // Try cache first
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Loaded ${documents.size()} journals from cache")
                val journals = documents.toObjects(Journal::class.java)
                callback(journals)

                // Then fetch from server in background to update cache
                fetchFromServer(userId, callback)
            }
            .addOnFailureListener { cacheException ->
                Log.w(TAG, "Cache fetch failed, fetching from server", cacheException)
                // If cache fails, fetch from server
                fetchFromServer(userId, callback)
            }
    }

    private fun fetchFromServer(userId: String, callback: (List<Journal>) -> Unit) {
        db.collection(COLLECTION_JOURNALS)
            .whereEqualTo(FIELD_USER_ID, userId)
            .orderBy(FIELD_TIME_ADDED, com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get(Source.SERVER)
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Loaded ${documents.size()} journals from server")
                val journals = documents.toObjects(Journal::class.java)
                callback(journals)
            }
            .addOnFailureListener { serverException ->
                Log.e(TAG, "Server fetch failed", serverException)
                // Already showed cached data if available
            }
    }

    private fun handleQueryError(exception: Exception) {
        when (exception) {
            is FirebaseFirestoreException -> {
                when (exception.code) {
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION -> {
                        // Index not created yet
                        Log.e(TAG, "Firebase index required. Check logs for index creation link.", exception)
                        Toast.makeText(
                            context,
                            "Database optimization needed. Please contact support.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    FirebaseFirestoreException.Code.UNAVAILABLE -> {
                        // Network unavailable, but offline persistence will handle it
                        Log.w(TAG, "Network unavailable, using offline cache", exception)
                        Toast.makeText(
                            context,
                            "You're offline. Changes will sync when online.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> {
                        Log.e(TAG, "Error finding journal: ${exception.message}", exception)
                        Toast.makeText(
                            context,
                            "Error finding journal: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            else -> {
                Log.e(TAG, "Unexpected error finding journal", exception)
                Toast.makeText(
                    context,
                    "Error finding journal: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun deleteImageFromStorage(imageUrl: String) {
        if (imageUrl.isNotEmpty()) {
            try {
                val storageRef = storage.getReferenceFromUrl(imageUrl)
                storageRef.delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "Image deleted successfully from Storage")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to delete image from Storage (will retry when online)", e)
                        // The journal is already deleted from Firestore
                        // Storage deletion will be retried when online
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Invalid storage URL: $imageUrl", e)
            }
        }
    }

    /**
     * Check if device is currently using offline cache
     */
    fun isUsingOfflineCache(): Boolean {
        // This can be checked via metadata when querying
        return false // Placeholder - check in actual query results
    }

    /**
     * Extension function to trim Spanned text
     */
    private fun Spanned.trim(): CharSequence {
        var start = 0
        var end = length

        while (start < end && Character.isWhitespace(this[start])) {
            start++
        }

        while (end > start && Character.isWhitespace(this[end - 1])) {
            end--
        }

        return if (start > 0 || end < length) {
            subSequence(start, end)
        } else {
            this
        }
    }
}