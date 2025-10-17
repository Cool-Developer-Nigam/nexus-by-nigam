package com.nigdroid.journal

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.nigdroid.journal.databinding.ActivityGeminiBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeminiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGeminiBinding
    private val messages = mutableListOf<Message>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var geminiHelper: GeminiHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityGeminiBinding.inflate(layoutInflater)
        setContentView(binding.root)

binding.btnBack.setOnClickListener {
            finish()
        }


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
            layoutManager = LinearLayoutManager(this@GeminiActivity).apply {
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
                // Call Gemini API
                val response = geminiHelper.sendMessage(userMessage)

                // Add bot response
                addMessage(Message(
                    text = response,
                    isFromUser = false
                ))

            } catch (e: Exception) {
                Log.e("GeminiActivity", "Error in getBotResponse", e)
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
}