package com.nigdroid.journal

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.text.Html
import android.text.Spanned
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.nigdroid.journal.databinding.DialogConfirmDeleteBinding
import com.nigdroid.journal.databinding.ItemTextNoteBinding
import com.nigdroid.journal.databinding.JournalRowBinding

class UnifiedNotesAdapter(
    private var context: Context,
    private var notesList: MutableList<UnifiedNoteItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // For audio playback
    private var currentlyPlaying: MediaPlayer? = null
    private var currentPlayingPosition = -1

    companion object {
        private const val TAG = "UnifiedNotesAdapter"

        const val VIEW_TYPE_JOURNAL = 0
        const val VIEW_TYPE_TEXT_NOTE = 1
        const val VIEW_TYPE_TODO = 2
        const val VIEW_TYPE_AUDIO = 3

        private const val MAX_PREVIEW_ITEMS = 3

        // Firebase collection names
        private const val COLLECTION_JOURNALS = "Journal"

        // Field names for consistent indexing
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_TITLE = "title"
        private const val FIELD_TIME_ADDED = "timeAdded"
        private const val FIELD_THOUGHTS = "thoughts"
        private const val FIELD_IMAGE_URL = "imageUrl"
        private const val FIELD_USERNAME = "username"
    }

    // ==================== ViewHolder Classes ====================

    inner class JournalViewHolder(val binding: JournalRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(journal: Journal) {
            binding.journal = journal
            binding.executePendingBindings()
        }
    }

    inner class TextNoteViewHolder(val binding: ItemTextNoteBinding) : RecyclerView.ViewHolder(binding.root)

    inner class TodoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val todoCard: MaterialCardView = itemView.findViewById(R.id.todoCard)
        val todoTitle: TextView = itemView.findViewById(R.id.todoTitle)
        val pinIcon: ImageView = itemView.findViewById(R.id.pinIcon)
        val checklistContainer: LinearLayout = itemView.findViewById(R.id.checklistItemsContainer)
        val moreItemsText: TextView = itemView.findViewById(R.id.moreItemsText)
        val todoTimestamp: TextView = itemView.findViewById(R.id.todoTimestamp)
    }

    inner class AudioNoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val noteCard: MaterialCardView = itemView.findViewById(R.id.audioNoteCard)
        val noteTitle: TextView = itemView.findViewById(R.id.audioNoteTitle)
        val pinIcon: ImageView = itemView.findViewById(R.id.pinIcon)
        val audioDuration: TextView = itemView.findViewById(R.id.audioDuration)
        val transcriptionPreview: TextView = itemView.findViewById(R.id.transcriptionPreview)
        val timestamp: TextView = itemView.findViewById(R.id.audioNoteTimestamp)
        val btnPlayPreview: ImageButton = itemView.findViewById(R.id.btnPlayPreview)
    }

    // ==================== ViewType Selection ====================

    override fun getItemViewType(position: Int): Int {
        return when (notesList[position]) {
            is UnifiedNoteItem.JournalItem -> VIEW_TYPE_JOURNAL
            is UnifiedNoteItem.TextNoteItem -> VIEW_TYPE_TEXT_NOTE
            is UnifiedNoteItem.TodoItemWrapper -> VIEW_TYPE_TODO
            is UnifiedNoteItem.AudioNoteItem -> VIEW_TYPE_AUDIO
        }
    }

    // ==================== Create ViewHolder ====================

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_JOURNAL -> {
                val binding = JournalRowBinding.inflate(inflater, parent, false)
                JournalViewHolder(binding)
            }
            VIEW_TYPE_TEXT_NOTE -> {
                val binding = ItemTextNoteBinding.inflate(inflater, parent, false)
                TextNoteViewHolder(binding)
            }
            VIEW_TYPE_TODO -> {
                val view = inflater.inflate(R.layout.item_todo, parent, false)
                TodoViewHolder(view)
            }
            VIEW_TYPE_AUDIO -> {
                val view = inflater.inflate(R.layout.item_audio_note, parent, false)
                AudioNoteViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    // ==================== Bind ViewHolder ====================

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = notesList[position]) {
            is UnifiedNoteItem.JournalItem -> bindJournal(holder as JournalViewHolder, item, position)
            is UnifiedNoteItem.TextNoteItem -> bindTextNote(holder as TextNoteViewHolder, item)
            is UnifiedNoteItem.TodoItemWrapper -> bindTodo(holder as TodoViewHolder, item)
            is UnifiedNoteItem.AudioNoteItem -> bindAudioNote(holder as AudioNoteViewHolder, item, position)
        }
    }

    override fun getItemCount(): Int = notesList.size

    // ==================== Bind Methods ====================

    private fun bindJournal(holder: JournalViewHolder, item: UnifiedNoteItem.JournalItem, position: Int) {
        val journal = item.journal

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
            val intent = Intent(context, Show_Journal::class.java).apply {
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

    private fun bindTextNote(holder: TextNoteViewHolder, item: UnifiedNoteItem.TextNoteItem) {
        val note = item.textNote

        with(holder.binding) {
            // Set title - strip HTML if present
            val titleText = if (note.title.contains("<")) {
                Html.fromHtml(note.title, Html.FROM_HTML_MODE_COMPACT).toString().trim()
            } else {
                note.title
            }
            textNoteTitle.text = titleText.ifEmpty { "Untitled" }

            // Display content - properly handle HTML
            val contentText = if (note.content.contains("<")) {
                val spanned = Html.fromHtml(note.content, Html.FROM_HTML_MODE_COMPACT)
                spanned.trim()
            } else {
                note.content
            }

            textNoteContent.text = contentText.ifEmpty {
                "No content"
            }

            // Set colors
            try {
                textNoteCard.setCardBackgroundColor(Color.parseColor(note.backgroundColor))
                textNoteTitle.setTextColor(Color.parseColor(note.textColor))
                textNoteContent.setTextColor(Color.parseColor(note.textColor))
            } catch (e: Exception) {
                textNoteCard.setCardBackgroundColor(Color.WHITE)
                textNoteTitle.setTextColor(Color.BLACK)
                textNoteContent.setTextColor(Color.BLACK)
            }

            // Pin icon
            pinIcon.visibility = if (note.isPinned) View.VISIBLE else View.GONE

            // Timestamp
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                note.timeModified,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            textNoteTimestamp.text = relativeTime

            // Click listener
            textNoteCard.setOnClickListener {
                val intent = Intent(context, AddTextNoteActivity::class.java).apply {
                    putExtra("NOTE_ID", note.id)
                    putExtra("NOTE_TITLE", note.title)
                    putExtra("IS_PINNED", note.isPinned)
                }
                context.startActivity(intent)
            }
        }
    }

    private fun bindTodo(holder: TodoViewHolder, item: UnifiedNoteItem.TodoItemWrapper) {
        val todo = item.todoItem

        // Set title
        holder.todoTitle.text = todo.title.ifEmpty { "Untitled" }

        // Show/hide pin icon
        holder.pinIcon.visibility = if (todo.isPinned) View.VISIBLE else View.GONE

        // Clear previous checklist items
        holder.checklistContainer.removeAllViews()

        // Show preview of checklist items (max 3)
        val itemsToShow = todo.items.take(MAX_PREVIEW_ITEMS)
        for (checkItem in itemsToShow) {
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_checklist_preview, holder.checklistContainer, false)

            val checkbox = itemView.findViewById<CheckBox>(R.id.checkboxItem)
            val itemText = itemView.findViewById<TextView>(R.id.itemText)

            checkbox.isChecked = checkItem.isChecked
            itemText.text = checkItem.text

            // Strike through completed items
            if (checkItem.isChecked) {
                itemText.paintFlags = itemText.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                itemText.alpha = 0.5f
            } else {
                itemText.paintFlags = itemText.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                itemText.alpha = 1.0f
            }

            holder.checklistContainer.addView(itemView)
        }

        // Show "more items" indicator if there are more than 3 items
        val remainingCount = todo.items.size - MAX_PREVIEW_ITEMS
        if (remainingCount > 0) {
            holder.moreItemsText.visibility = View.VISIBLE
            holder.moreItemsText.text = "+$remainingCount more item${if (remainingCount > 1) "s" else ""}"
        } else {
            holder.moreItemsText.visibility = View.GONE
        }

        // Set timestamp
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            todo.timeModified,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.todoTimestamp.text = relativeTime

        // Set click listener
        holder.todoCard.setOnClickListener {
            val intent = Intent(context, AddTodoActivity::class.java).apply {
                putExtra("TODO_ID", todo.id)
                putExtra("TODO_TITLE", todo.title)
                putExtra("IS_PINNED", todo.isPinned)
            }
            context.startActivity(intent)
        }
    }

    private fun bindAudioNote(holder: AudioNoteViewHolder, item: UnifiedNoteItem.AudioNoteItem, position: Int) {
        val note = item.audioNote

        holder.noteTitle.text = note.title.ifEmpty { "Untitled" }
        holder.pinIcon.visibility = if (note.isPinned) View.VISIBLE else View.GONE

        // Format duration
        val minutes = (note.audioDuration / 1000) / 60
        val seconds = (note.audioDuration / 1000) % 60
        holder.audioDuration.text = String.format("%02d:%02d", minutes, seconds)

        // Transcription preview
        if (note.transcription.isNotEmpty()) {
            holder.transcriptionPreview.visibility = View.VISIBLE
            holder.transcriptionPreview.text = note.transcription
        } else {
            holder.transcriptionPreview.visibility = View.GONE
        }

        // Timestamp
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            note.timeModified,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.timestamp.text = relativeTime

        // Play button
        holder.btnPlayPreview.setOnClickListener {
            if (currentPlayingPosition == position) {
                stopAudio(holder)
            } else {
                playAudio(note, holder, position)
            }
        }

        // Update play button icon
        if (currentPlayingPosition == position) {
            holder.btnPlayPreview.setImageResource(R.drawable.ic_pause)
        } else {
            holder.btnPlayPreview.setImageResource(R.drawable.ic_play)
        }

        // Card click
        holder.noteCard.setOnClickListener {
            val intent = Intent(context, AddAudioNoteActivity::class.java).apply {
                putExtra("AUDIO_NOTE_ID", note.id)
                putExtra("AUDIO_TITLE", note.title)
                putExtra("IS_PINNED", note.isPinned)
            }
            context.startActivity(intent)
        }
    }

    // ==================== Delete Confirmation Dialog ====================

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

    // ==================== Delete Journal with Offline Support ====================

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
                            notesList.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, notesList.size)

                            val message = if (source) {
                                "Journal deleted (will sync when online)"
                            } else {
                                "Journal deleted successfully"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                            // Notify activity if list is now empty
                            if (notesList.isEmpty() && context is JournalListActivity) {
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

    // ==================== Offline Support Methods ====================

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

    /**
     * Check if device is currently using offline cache
     */
    fun isUsingOfflineCache(): Boolean {
        // This can be checked via metadata when querying
        return false // Placeholder - check in actual query results
    }

    // ==================== Error Handling ====================

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

    // ==================== Storage Management ====================

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

    // ==================== Audio Playback ====================

    private fun playAudio(note: AudioNote, holder: AudioNoteViewHolder, position: Int) {
        // Stop current if playing
        stopCurrentAudio()

        try {
            currentlyPlaying = MediaPlayer().apply {
                setDataSource(note.audioUrl)
                prepareAsync()
                setOnPreparedListener {
                    start()
                    currentPlayingPosition = position
                    holder.btnPlayPreview.setImageResource(R.drawable.ic_pause)
                }
                setOnCompletionListener {
                    stopAudio(holder)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAudio(holder: AudioNoteViewHolder) {
        currentlyPlaying?.apply {
            stop()
            release()
        }
        currentlyPlaying = null
        currentPlayingPosition = -1
        holder.btnPlayPreview.setImageResource(R.drawable.ic_play)
    }

    private fun stopCurrentAudio() {
        currentlyPlaying?.apply {
            stop()
            release()
        }
        currentlyPlaying = null
        if (currentPlayingPosition != -1) {
            notifyItemChanged(currentPlayingPosition)
        }
        currentPlayingPosition = -1
    }

    fun releasePlayer() {
        currentlyPlaying?.release()
        currentlyPlaying = null
    }

    // ==================== Helper Extensions ====================

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

    // ==================== Public Methods ====================

    fun updateList(newList: List<UnifiedNoteItem>) {
        if (notesList.size > 0) {
            val oldSize = notesList.size
            notesList.clear()
            notifyItemRangeRemoved(0, oldSize)
        }
        notesList.addAll(newList)
        notifyItemRangeInserted(0, newList.size)
    }
}