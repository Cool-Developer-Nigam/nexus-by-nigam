package com.nigdroid.journal

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.nigdroid.journal.databinding.ActivityMainBinding
import com.yourpackage.app.HomeFragment

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this

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

        // FAB click listener
        binding.fabAdd.setOnClickListener {
            loadFragment(AddFragment())
            // Clear bottom nav selection
            binding.bottomNav.menu.findItem(R.id.nav_placeholder)?.isChecked = true
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
                Toast.makeText(this, "Logout", Toast.LENGTH_SHORT).show()
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
}