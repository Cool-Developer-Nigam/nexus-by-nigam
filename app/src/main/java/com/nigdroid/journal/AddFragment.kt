package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment

class AddFragment : Fragment() {

    private var optionsContainer: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add, container, false)

        val backgroundDismiss = view.findViewById<View>(R.id.backgroundDismiss)
        optionsContainer = view.findViewById(R.id.optionsContainer)
        val optionJournal = view.findViewById<LinearLayout>(R.id.optionJournal)
        val optionTextNote = view.findViewById<LinearLayout>(R.id.optionTextNote)
        val optionTodoNote = view.findViewById<LinearLayout>(R.id.optionTodoNote)
        val optionAudioNote = view.findViewById<LinearLayout>(R.id.optionAudioNote)

        // Dismiss listener
        backgroundDismiss.setOnClickListener {
            dismissFragment()
        }

        // Option listeners
        optionJournal.setOnClickListener {
            handleOptionClick("Journal")
        }

        optionTextNote.setOnClickListener {
            handleOptionClick("Text Note")
        }

        optionTodoNote.setOnClickListener {
            handleOptionClick("Todo Note")
        }

        optionAudioNote.setOnClickListener {
            handleOptionClick("Audio Note")
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Animate after view is created
        animateEntry(view, optionsContainer!!)
    }

    private fun animateEntry(rootView: View, optionsContainer: View) {
        // Set initial state
        rootView.alpha = 0f
        optionsContainer.translationY = 600f

        // Animate
        rootView.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        optionsContainer.animate()
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun handleOptionClick(optionName: String) {
        Toast.makeText(requireContext(), "$optionName selected", Toast.LENGTH_SHORT).show()

        // TODO: Navigate to respective screens
        // Example:
         when (optionName) {
             "Journal" -> startActivity(Intent(requireContext(), JournalListActivity::class.java))
             "Text Note" -> startActivity(Intent(requireContext(), TextNotesListActivity::class.java))
             "Todo Note" -> startActivity(Intent(requireContext(), TodoListActivity::class.java))
             "Audio Note" -> startActivity(Intent(requireContext(), AudioNotesListActivity::class.java))
         }

        dismissFragment()
    }

    private fun dismissFragment() {
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        optionsContainer = null
    }
}