package com.nigdroid.journal

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.nigdroid.journal.databinding.DialogConfirmDeleteBinding
import com.nigdroid.journal.databinding.JournalRowBinding

class JournalRecyclerAdapter(
    var context: Context,
    var journalList: MutableList<Journal>
) : RecyclerView.Adapter<JournalRecyclerAdapter.MyViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

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
        holder.bind(journal)

        // Set up delete button click listener
        holder.binding.deleteBtn.setOnClickListener {
            showDeleteConfirmationDialog(journal, position)
        }

        // Set up item click listener to view full journal
        holder.binding.root.setOnClickListener {
            val intent = android.content.Intent(context, Show_Journal::class.java).apply {
                putExtra("title", journal.title)
                putExtra("thoughts", journal.thoughts)
                putExtra("imageUrl", journal.imageUrl)
                putExtra("userId", journal.userId)
                putExtra("timeAdded", journal.timeAdded)
                putExtra("username", journal.username)
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
        // Query Firestore to find the document with matching data
        db.collection("Journal")
            .whereEqualTo("userId", journal.userId)
            .whereEqualTo("title", journal.title)
            .whereEqualTo("timeAdded", journal.timeAdded)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val documentId = documents.documents[0].id

                    // Delete from Firestore
                    db.collection("Journal")
                        .document(documentId)
                        .delete()
                        .addOnSuccessListener {
                            // Delete image from Storage
                            deleteImageFromStorage(journal.imageUrl)

                            // Remove from list and update RecyclerView
                            journalList.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, journalList.size)

                            Toast.makeText(context,"Journal deleted successfully", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context,"Failed to delete journal: ${e.message}",Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(context,"Journal not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context,"Error finding journal: ${e.message}",Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteImageFromStorage(imageUrl: String) {
        if (imageUrl.isNotEmpty()) {
            try {
                val storageRef = storage.getReferenceFromUrl(imageUrl)
                storageRef.delete()
                    .addOnSuccessListener {
                        // Image deleted successfully
                    }
                    .addOnFailureListener { e ->
                        // Log or handle error silently
                        // The journal is already deleted from Firestore
                    }
            } catch (e: Exception) {
                // Handle invalid URL
            }
        }
    }
}