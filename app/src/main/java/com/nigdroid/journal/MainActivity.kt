package com.nigdroid.journal

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.nigdroid.journal.databinding.ActivityMainBinding
import com.nigdroid.journal.databinding.DialogConfirmSignoutBinding

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private var isFabMenuOpen = false

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
            // Close FAB menu if open
            if (isFabMenuOpen) {
                closeFabMenu()
            }

            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_chatbot -> {
                    loadFragment(BookFragment())
                    true
                }
                else -> false
            }
        }

        // FAB click listener
        binding.fabAdd.setOnClickListener {
            toggleFabMenu()
        }

        // Setup menu option click listener
        binding.fabMenu.onOptionSelectedListener = { index ->
            handleMenuOptionSelected(index)
        }

        // Setup menu state change listener for auto-rotation
        binding.fabMenu.onMenuStateChangeListener = { isOpen ->
            isFabMenuOpen = isOpen

            // Auto-rotate FAB based on menu state
            val targetRotation = if (isOpen) 45f else 0f
            ObjectAnimator.ofFloat(binding.fabAdd, "rotation", targetRotation).apply {
                duration = if (isOpen) 400 else 350
                interpolator = if (isOpen) OvershootInterpolator(1.2f) else DecelerateInterpolator()
                start()
            }

            // Update bottom navigation state
            binding.bottomNav.setMenuOpenState(isOpen, true)
        }
    }

    private fun toggleFabMenu() {
        isFabMenuOpen = !isFabMenuOpen

        // Rotate FAB
        val targetRotation = if (isFabMenuOpen) 45f else 0f
        ObjectAnimator.ofFloat(binding.fabAdd, "rotation", targetRotation).apply {
            duration = 400
            interpolator = OvershootInterpolator(1.2f)
            start()
        }

        // Bring views to front before opening
        if (isFabMenuOpen) {
            binding.fabMenu.bringToFront()
            binding.fabAdd.bringToFront()
        }

        // Animate bottom navigation state
        binding.bottomNav.setMenuOpenState(isFabMenuOpen, true)

        // Toggle FAB menu overlay
        binding.fabMenu.toggle()
    }

    private fun closeFabMenu() {
        if (!isFabMenuOpen) return

        isFabMenuOpen = false

        // Rotate FAB back to original position
        ObjectAnimator.ofFloat(binding.fabAdd, "rotation", 0f).apply {
            duration = 350
            interpolator = DecelerateInterpolator()
            start()
        }

        // Update bottom navigation state
        binding.bottomNav.setMenuOpenState(false, true)

        // Close FAB menu overlay
        if (binding.fabMenu.visibility == android.view.View.VISIBLE) {
            binding.fabMenu.toggle()
        }
    }

    private fun handleMenuOptionSelected(index: Int) {
        val intent = when (index) {
            0 -> Intent(this, AddJournalActivity::class.java)
            3 -> Intent(this, AddTextNoteActivity::class.java)
            2 -> Intent(this, AddAudioNoteActivity::class.java)
            1 -> Intent(this, AddTodoActivity::class.java)
            else -> null
        }

        intent?.let {
            startActivity(it)
        } ?: run {
            val optionName = when (index) {
                0 -> "Journal"
                3 -> "Text Note"
                2 -> "Audio Note"
                1 -> "Todo"
                else -> "Option"
            }
            Toast.makeText(this, "$optionName selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadNavHeaderProfile() {
        val headerView = binding.navView.getHeaderView(0)
        val profileImage = headerView.findViewById<ImageView>(R.id.nav_header_profile_image)
        val profileName = headerView.findViewById<TextView>(R.id.nav_header_profile_name)
        val profileEmail = headerView.findViewById<TextView>(R.id.nav_header_profile_email)

        val currentUser = firebaseAuth.currentUser

        if (currentUser != null) {
            val displayName = currentUser.displayName
            val email = currentUser.email

            val userName = when {
                !displayName.isNullOrEmpty() -> displayName
                !email.isNullOrEmpty() -> email.substringBefore("@")
                else -> "User"
            }

            profileName.text = userName
            profileEmail.text = email ?: "No email"

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
            profileName.text = "Guest"
            profileEmail.text = "Not logged in"
            profileImage.setImageResource(R.drawable.ic_profile)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_profile -> {
                val intent = Intent(this, ProfileActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_gemini->{
                val intent = Intent(this, GeminiActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_logout -> {
                showDeleteConfirmationDialog()
            }
            R.id.nav_all_notes->{
                val intent = Intent(this, AllActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_trash->{
                val intent = Intent(this, TrashActivity::class.java)
                startActivity(intent)
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showDeleteConfirmationDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)
        val dialogBinding = DialogConfirmSignoutBinding.inflate(layoutInflater)

        dialogBinding.btnDelete.setOnClickListener {
            firebaseAuth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            finish()
        }

        dialogBinding.btnCancel.setOnClickListener {
            Toast.makeText(this, "Signout Cancelled", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onBackPressed() {
        when {
            isFabMenuOpen -> {
                closeFabMenu()
            }
            binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadNavHeaderProfile()
    }
}