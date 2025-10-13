package com.nigdroid.journal

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.nigdroid.journal.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this

        firebaseAuth = FirebaseAuth.getInstance()

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        // Setup drawer toggle
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Setup navigation view
        binding.navView.setNavigationItemSelectedListener(this)

        // Load user profile in nav header
        loadNavHeaderProfile()

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
            binding.bottomNav.selectedItemId = R.id.nav_home
        }

        // Bottom Navigation item selection
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_chatbot -> {
                    loadFragment(ChatbotFragment())
                    true
                }
                else -> false
            }
        }

        // FAB click listener - OPTIMIZED
        binding.fabAdd.setOnClickListener {
            // Check if fragment is already showing
            val existingFragment = supportFragmentManager.findFragmentByTag("AddFragment")
            if (existingFragment == null) {
                showAddFragment()
            }
        }
    }

    private fun showAddFragment() {
        // Use add() instead of replace() and add to android.R.id.content
        // This adds the fragment over the entire screen including MainActivity
        supportFragmentManager.beginTransaction()
            .add(android.R.id.content, AddFragment(), "AddFragment")
            .addToBackStack(null)
            .commit()
    }

    private fun loadNavHeaderProfile() {
        val headerView = binding.navView.getHeaderView(0)
        val profileImage = headerView.findViewById<ImageView>(R.id.nav_header_profile_image)
        val profileName = headerView.findViewById<TextView>(R.id.nav_header_profile_name)
        val profileEmail = headerView.findViewById<TextView>(R.id.nav_header_profile_email)

        val currentUser = firebaseAuth.currentUser

        if (currentUser != null) {
            // Set name
            val displayName = currentUser.displayName
            val email = currentUser.email

            val userName = when {
                !displayName.isNullOrEmpty() -> displayName
                !email.isNullOrEmpty() -> email.substringBefore("@")
                else -> "User"
            }

            profileName.text = userName
            profileEmail.text = email ?: "No email"

            // Load profile photo
            val photoUrl = currentUser.photoUrl
            if (photoUrl != null) {
                Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(profileImage)
            } else {
                profileImage.setImageResource(R.drawable.ic_profile)
            }
        } else {
            // Guest user
            profileName.text = "Guest"
            profileEmail.text = "Not logged in"
            profileImage.setImageResource(R.drawable.ic_profile)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                loadFragment(HomeFragment())
                binding.bottomNav.selectedItemId = R.id.nav_home
            }
            R.id.nav_settings -> {
                // Navigate to settings
                Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_share -> {
                // Share app
                Toast.makeText(this, "Share", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_about -> {
                // Show about
                Toast.makeText(this, "About Us", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_logout -> {
                // Handle logout
                firebaseAuth.signOut()
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                // Navigate to login
                finish()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh profile when activity resumes
        loadNavHeaderProfile()
    }
}