package com.nigdroid.journal

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.google.firebase.auth.FirebaseAuth
import com.nigdroid.journal.databinding.ActivityForgotPasswordBinding

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window transparent and show content behind
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Enable blur effect on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            )
            window.attributes.blurBehindRadius = 20
        }

        binding = DataBindingUtil.setContentView(this, R.layout.activity_forgot_password)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        setupClickListeners()
        setupDismissOnOutsideClick()
    }

    private fun setupClickListeners() {
        // Send reset email button
        binding.btnSendResetEmail.setOnClickListener {
            sendPasswordResetEmail()
        }

        // Back to login button
        binding.tvBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun setupDismissOnOutsideClick() {
        // Dismiss when clicking outside the card
        binding.root.setOnClickListener {
            finish()
        }

        // Prevent clicks on the card from dismissing
        binding.cardContainer.setOnClickListener {
            // Do nothing - consume the click
        }
    }

    private fun sendPasswordResetEmail() {
        val email = binding.etEmail.text.toString().trim()

        // Validate email
        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.error = "Email is required"
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Please enter a valid email"
            return
        }

        // Clear any previous errors
        binding.tilEmail.error = null

        // Disable button to prevent multiple clicks
        binding.btnSendResetEmail.isEnabled = false

        // Send password reset email
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                binding.btnSendResetEmail.isEnabled = true

                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Password reset email sent to $email",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        "Failed to send reset email: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { exception ->
                binding.btnSendResetEmail.isEnabled = true
                Toast.makeText(
                    this,
                    "Error: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}