package com.nigdroid.journal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.nigdroid.journal.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var firebaseAuth: FirebaseAuth

    private val TAG = "HomeFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_home, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        firebaseAuth = FirebaseAuth.getInstance()

        // Load user profile
        loadUserProfile()

        // Set up navigation
        setupNavigation()

        return binding.root
    }

    private fun loadUserProfile() {
        val currentUser = firebaseAuth.currentUser

        if (currentUser == null) {
            Log.e(TAG, "No user logged in")
            binding.username.text = "Guest"
            return
        }

        Log.d(TAG, "User email: ${currentUser.email}")
        Log.d(TAG, "User displayName: ${currentUser.displayName}")
        Log.d(TAG, "User photoUrl: ${currentUser.photoUrl}")

        // Set user name
        val displayName = currentUser.displayName
        val email = currentUser.email

        val userName = when {
            !displayName.isNullOrEmpty() -> displayName
            !email.isNullOrEmpty() -> email.substringBefore("@")
            else -> "User"
        }

        binding.username.text = userName
        Log.d(TAG, "Setting username to: $userName")

        // Load profile photo
        val photoUrl = currentUser.photoUrl

        if (photoUrl != null) {
            Log.d(TAG, "Loading photo from: $photoUrl")
            // User has profile photo (from Google Sign-In)
            context?.let { ctx ->
                Glide.with(ctx)
                    .load(photoUrl)
                    .placeholder(R.drawable.profile_image)
                    .error(R.drawable.profile_image)
                    .circleCrop()
                    .into(binding.profileImage)
            }
        } else {
            Log.d(TAG, "No photo URL, using default")
            // No profile photo, use default
            binding.profileImage.setImageResource(R.drawable.profile_image)
        }

        // Save to SharedPreferences for offline access
        context?.let { ctx ->
            val sharedPref = ctx.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            sharedPref.edit().apply {
                putString("username", userName)
                putString("userEmail", email)
                putString("photoUrl", photoUrl?.toString())
                apply()
            }
        }
    }

    private fun setupNavigation() {
        binding.JournalCard.setOnClickListener {
            startActivity(Intent(requireContext(), JournalListActivity::class.java))
        }

        binding.TodoCard.setOnClickListener {
            startActivity(Intent(requireContext(), TodoListActivity::class.java))
        }

        binding.audioCard.setOnClickListener {
            startActivity(Intent(requireContext(), AudioNotesListActivity::class.java))
        }

        binding.textCard.setOnClickListener {
            startActivity(Intent(requireContext(), TextNotesListActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh profile when fragment is resumed
        loadUserProfile()
    }
}