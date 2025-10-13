package com.nigdroid.journal

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddOptionsDialog : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        // Make background dim and clickable
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup click listeners for cards
        view.findViewById<View>(R.id.cardJournal).setOnClickListener {
            handleOptionClick("Journal")
        }

        view.findViewById<View>(R.id.cardTextNote).setOnClickListener {
            handleOptionClick("Text Note")
        }

        view.findViewById<View>(R.id.cardTodo).setOnClickListener {
            handleOptionClick("Todo Note")
        }

        view.findViewById<View>(R.id.cardAudio).setOnClickListener {
            handleOptionClick("Audio Note")
        }
    }

    private fun handleOptionClick(optionName: String) {
        Toast.makeText(requireContext(), "$optionName selected", Toast.LENGTH_SHORT).show()

        // TODO: Navigate to respective screens

            when (optionName) {
                "Journal" -> {
                    startActivity(Intent(requireContext(), AddJournalActivity::class.java))
            }
            "Text Note" -> {
                startActivity(Intent(requireContext(), AddTextNoteActivity::class.java))
            }
            "Todo Note" -> {
                startActivity(Intent(requireContext(), AddTodoActivity::class.java))
            }
                "Audio Note" -> {
                    startActivity(Intent(requireContext(), AddAudioNoteActivity::class.java))
                }
        }

        dismiss()
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme
}