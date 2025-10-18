package com.nigdroid.journal

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Worker that automatically deletes items from trash after 7 days
 * Runs daily to check and clean up expired items
 */
class TrashCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    companion object {
        private const val TAG = "TrashCleanupWorker"
        private const val WORK_NAME = "trash_cleanup_work"
        private const val COLLECTION_TRASH = "Trash"
        private const val DAYS_BEFORE_DELETE = 7

        /**
         * Schedule the cleanup worker to run daily
         * Call this from your Application class or MainActivity
         */
        fun scheduleCleanup(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val cleanupRequest = PeriodicWorkRequestBuilder<TrashCleanupWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS) // First run after 1 hour
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )

            Log.d(TAG, "Trash cleanup worker scheduled")
        }

        /**
         * Run cleanup immediately (for testing or manual trigger)
         */
        fun runCleanupNow(context: Context) {
            val cleanupRequest = OneTimeWorkRequestBuilder<TrashCleanupWorker>()
                .build()

            WorkManager.getInstance(context).enqueue(cleanupRequest)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid

            if (userId == null) {
                Log.w(TAG, "No user logged in, skipping cleanup")
                return Result.success()
            }

            Log.d(TAG, "Starting trash cleanup for user: $userId")

            val deletedCount = cleanupExpiredItems(userId)

            Log.d(TAG, "Cleanup completed. Deleted $deletedCount items")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during trash cleanup", e)
            Result.retry()
        }
    }

    private suspend fun cleanupExpiredItems(userId: String): Int {
        val sevenDaysAgo = System.currentTimeMillis() - (DAYS_BEFORE_DELETE * 24 * 60 * 60 * 1000L)
        var deletedCount = 0

        try {
            val querySnapshot = db.collection(COLLECTION_TRASH)
                .whereEqualTo("userId", userId)
                .whereLessThan("trashedAt", sevenDaysAgo)
                .get()
                .await()

            querySnapshot.documents.forEach { document ->
                try {
                    val item = document.toObject(TrashedItem::class.java)

                    // Delete associated files from Storage
                    item?.let { deleteAssociatedFiles(it) }

                    // Delete document from Firestore
                    document.reference.delete().await()

                    deletedCount++
                    Log.d(TAG, "Deleted expired item: ${document.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete item: ${document.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query expired items", e)
            throw e
        }

        return deletedCount
    }

    private suspend fun deleteAssociatedFiles(item: TrashedItem) {
        val fileUrl = when (item.type) {
            "journal" -> item.originalData["imageUrl"] as? String
            "audio" -> item.originalData["audioUrl"] as? String
            else -> null
        }

        fileUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            try {
                val storageRef = storage.getReferenceFromUrl(url)
                storageRef.delete().await()
                Log.d(TAG, "Deleted file from storage: $url")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete file from storage: $url", e)
                // Don't throw - we still want to delete the Firestore document
            }
        }
    }
}
