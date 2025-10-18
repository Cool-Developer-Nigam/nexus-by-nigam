package com.nigdroid.journal

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object TrashHelper {
    private const val TAG = "TrashHelper"
    private const val COLLECTION_TRASH = "Trash"
    private val db = FirebaseFirestore.getInstance()

    fun moveTextNoteToTrash(
        note: TextNote,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // Generate document reference first
        val trashDocRef = db.collection(COLLECTION_TRASH).document()

        val trashedItem = TrashedItem(
            id = trashDocRef.id,  // Use the same ID
            originalId = note.id,
            userId = note.userId,
            type = "textNote",
            trashedAt = System.currentTimeMillis(),
            originalData = mapOf(
                "id" to note.id,
                "title" to note.title,
                "content" to note.content,
                "userId" to note.userId,
                "timeAdded" to note.timeAdded,
                "timeModified" to note.timeModified,
                "backgroundColor" to note.backgroundColor,
                "textColor" to note.textColor,
                "isPinned" to note.isPinned
            )
        )

        // Use the same document reference
        trashDocRef.set(trashedItem)
            .addOnSuccessListener {
                Log.d(TAG, "Text note moved to trash with ID: ${trashDocRef.id}")
                db.collection("TextNotes")
                    .document(note.id)
                    .delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "Text note deleted from original collection")
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to delete text note", e)
                        onFailure(e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to move text note to trash", e)
                onFailure(e)
            }
    }

    fun moveJournalToTrash(
        journal: Journal,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trashDocRef = db.collection(COLLECTION_TRASH).document()
        val uniqueId = "${journal.userId}_${journal.timeAdded}"

        val trashedItem = TrashedItem(
            id = trashDocRef.id,
            originalId = uniqueId,
            userId = journal.userId,
            type = "journal",
            trashedAt = System.currentTimeMillis(),
            originalData = mapOf(
                "title" to journal.title,
                "thoughts" to journal.thoughts,
                "imageUrl" to journal.imageUrl,
                "userId" to journal.userId,
                "timeAdded" to journal.timeAdded,
                "username" to journal.username,
                "isPinned" to journal.isPinned
            )
        )

        trashDocRef.set(trashedItem)
            .addOnSuccessListener {
                Log.d(TAG, "Journal moved to trash with ID: ${trashDocRef.id}")
                deleteJournalFromOriginal(journal, onSuccess, onFailure)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to move journal to trash", e)
                onFailure(e)
            }
    }

    private fun deleteJournalFromOriginal(
        journal: Journal,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("Journal")
            .whereEqualTo("userId", journal.userId)
            .whereEqualTo("timeAdded", journal.timeAdded)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val batch = db.batch()
                    documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            Log.d(TAG, "Journal deleted from original collection")
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to delete journal from original", e)
                            onFailure(e)
                        }
                } else {
                    Log.w(TAG, "Journal not found in original collection")
                    onFailure(Exception("Journal not found"))
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to query journal", e)
                onFailure(e)
            }
    }

    fun moveTodoToTrash(
        todo: TodoItem,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trashDocRef = db.collection(COLLECTION_TRASH).document()

        val trashedItem = TrashedItem(
            id = trashDocRef.id,
            originalId = todo.id,
            userId = todo.userId,
            type = "todo",
            trashedAt = System.currentTimeMillis(),
            originalData = mapOf(
                "id" to todo.id,
                "title" to todo.title,
                "items" to todo.items.map { mapOf("text" to it.text, "isChecked" to it.isChecked) },
                "userId" to todo.userId,
                "timeAdded" to todo.timeAdded,
                "timeModified" to todo.timeModified,
                "isPinned" to todo.isPinned
            )
        )

        trashDocRef.set(trashedItem)
            .addOnSuccessListener {
                Log.d(TAG, "Todo moved to trash with ID: ${trashDocRef.id}")
                db.collection("TodoItems")
                    .document(todo.id)
                    .delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "Todo deleted from original collection")
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to delete todo", e)
                        onFailure(e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to move todo to trash", e)
                onFailure(e)
            }
    }

    fun moveAudioNoteToTrash(
        audio: AudioNote,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trashDocRef = db.collection(COLLECTION_TRASH).document()

        val trashedItem = TrashedItem(
            id = trashDocRef.id,
            originalId = audio.id,
            userId = audio.userId,
            type = "audio",
            trashedAt = System.currentTimeMillis(),
            originalData = mapOf(
                "id" to audio.id,
                "title" to audio.title,
                "audioUrl" to audio.audioUrl,
                "audioDuration" to audio.audioDuration,
                "transcription" to audio.transcription,
                "userId" to audio.userId,
                "timeAdded" to audio.timeAdded,
                "timeModified" to audio.timeModified,
                "isPinned" to audio.isPinned
            )
        )

        trashDocRef.set(trashedItem)
            .addOnSuccessListener {
                Log.d(TAG, "Audio note moved to trash with ID: ${trashDocRef.id}")
                db.collection("AudioNotes")
                    .document(audio.id)
                    .delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "Audio note deleted from original collection")
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to delete audio note", e)
                        onFailure(e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to move audio note to trash", e)
                onFailure(e)
            }
    }

    fun scheduleAutoDelete(userId: String) {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

        db.collection(COLLECTION_TRASH)
            .whereEqualTo("userId", userId)
            .whereLessThan("trashedAt", sevenDaysAgo)
            .get()
            .addOnSuccessListener { documents ->
                documents.forEach { doc ->
                    db.collection(COLLECTION_TRASH)
                        .document(doc.id)
                        .delete()
                        .addOnSuccessListener {
                            Log.d(TAG, "Auto-deleted expired item: ${doc.id}")
                        }
                }
            }
    }
}