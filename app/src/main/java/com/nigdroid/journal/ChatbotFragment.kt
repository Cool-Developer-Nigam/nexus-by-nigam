package com.nigdroid.journal

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nigdroid.journal.databinding.FragmentChatbotBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatbotFragment : Fragment() {

    private var _binding: FragmentChatbotBinding? = null
    private val binding get() = _binding!!

    private val messages = mutableListOf<Message>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var geminiHelper: GeminiHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatbotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Debug API key
        Log.d("ChatbotFragment", "API Key from constants: ${ApiConstants.GEMINI_API_KEY.take(10)}...")
        Log.d("ChatbotFragment", "API Key length: ${ApiConstants.GEMINI_API_KEY.length}")
        Log.d("ChatbotFragment", "API Key starts with AIza: ${ApiConstants.GEMINI_API_KEY.startsWith("AIza")}")

        // Initialize Gemini
        geminiHelper = GeminiHelper()

        setupRecyclerView()
        setupClickListeners()

        // Add welcome message
        addMessage(Message(
            text = "Hello! I'm your AI assistant powered by Gemini. How can I help you today?",
            isFromUser = false
        ))
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages)
        binding.recyclerViewMessages.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun sendMessage() {
        val text = binding.editTextMessage.text.toString().trim()

        if (text.isEmpty()) {
            return
        }

        // Add user message
        addMessage(Message(text = text, isFromUser = true))

        // Clear input
        binding.editTextMessage.text.clear()

        // Get bot response
        getBotResponse(text)
    }

    private fun addMessage(message: Message) {
        chatAdapter.addMessage(message)
        binding.recyclerViewMessages.smoothScrollToPosition(messages.size - 1)
    }

    private fun getBotResponse(userMessage: String) {
        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.buttonSend.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d("ChatbotFragment", "Getting bot response for: $userMessage")

                // Call Gemini API
                val response = geminiHelper.sendMessage(userMessage)

                Log.d("ChatbotFragment", "Got response: $response")

                // Add bot response
                addMessage(Message(
                    text = response,
                    isFromUser = false
                ))

            } catch (e: Exception) {
                Log.e("ChatbotFragment", "Error in getBotResponse", e)
                addMessage(Message(
                    text = "Sorry, I encountered an error: ${e.message}",
                    isFromUser = false
                ))
            } finally {
                // Hide loading
                binding.progressBar.visibility = View.GONE
                binding.buttonSend.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}