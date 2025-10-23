package com.nigdroid.journal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.nigdroid.journal.databinding.ActivityProfileBinding
import com.nigdroid.journal.databinding.DialogConfirmImageBinding
import com.nigdroid.journal.databinding.DialogEditProfileBinding
import java.util.UUID

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var selectedImageUri: Uri? = null

    // Activity result launcher for picking image from gallery
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri = result.data?.data
            imageUri?.let { uri ->
                // Show confirmation dialog before uploading
                showImageConfirmationDialog(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_profile)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        loadUserProfile()
        setupClickListeners()
        animateEntrance()
    }

    private fun animateEntrance() {
        // Animate profile image with scale and fade
        binding.profileImage.alpha = 0f
        binding.profileImage.scaleX = 0.8f
        binding.profileImage.scaleY = 0.8f

        binding.profileImage.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setStartDelay(100)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate change photo button
        binding.changePhotoButton.alpha = 0f
        binding.changePhotoButton.scaleX = 0.7f
        binding.changePhotoButton.scaleY = 0.7f

        binding.changePhotoButton.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setStartDelay(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate user info card
        binding.root.findViewById<androidx.cardview.widget.CardView>(R.id.userInfoCard)?.let { card ->
            card.alpha = 0f
            card.translationY = 30f

            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .setStartDelay(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        // Animate account settings card
        binding.root.findViewById<androidx.cardview.widget.CardView>(R.id.accountSettingsCard)?.let { card ->
            card.alpha = 0f
            card.translationY = 30f

            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .setStartDelay(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        // Animate logout button
        binding.logoutButton.alpha = 0f
        binding.logoutButton.translationY = 20f

        binding.logoutButton.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate delete account button
        binding.deleteAccountButton.alpha = 0f
        binding.deleteAccountButton.translationY = 20f

        binding.deleteAccountButton.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.editButton.setOnClickListener {
            showEditProfileDialog()
        }

        binding.changePhotoButton.setOnClickListener {
            openImagePicker()
        }

        binding.changePasswordLayout.setOnClickListener {
            changePassword()
        }

        binding.privacySettingsLayout.setOnClickListener {
            showEditProfileDialog()
        }

        binding.logoutButton.setOnClickListener {
            showLogoutDialog()
        }

        binding.deleteAccountButton.setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load data from SharedPreferences first (this is where updated data is stored)
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val savedName = sharedPref.getString("username", currentUser.displayName ?: "Unknown User")
        val savedBio = sharedPref.getString("userBio", "Software Developer | Passionate about coding")
        val savedPhone = sharedPref.getString("userPhone", currentUser.phoneNumber ?: "Not provided")

        // Create UserProfile object with saved data
        val user = UserProfile(
            name = savedName ?: "Unknown User",
            email = currentUser.email ?: "No email",
            phone = savedPhone ?: "Not provided",
            bio = savedBio ?: "Software Developer | Passionate about coding",
            profileImageUrl = currentUser.photoUrl?.toString() ?: ""
        )

        // Set user data to binding
        binding.user = user

        // Also update individual TextViews to ensure they show the correct data
        binding.userName.text = user.name
        binding.userEmail.text = user.email
        binding.userBio.text = user.bio
        binding.userPhone.text = user.phone

        // Load profile image with Glide
        if (user.profileImageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(user.profileImageUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(binding.profileImage)
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun showImageConfirmationDialog(imageUri: Uri) {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)
        val dialogBinding = DialogConfirmImageBinding.inflate(layoutInflater)

        // Preview the selected image in the dialog
        Glide.with(this)
            .load(imageUri)
            .centerCrop()
            .into(dialogBinding.previewImage)

        dialogBinding.btnConfirmYes.setOnClickListener {
            selectedImageUri = imageUri

            // Display selected image in profile with animation
            binding.profileImage.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(150)
                .withEndAction {
                    Glide.with(this)
                        .load(selectedImageUri)
                        .centerCrop()
                        .into(binding.profileImage)

                    binding.profileImage.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                }
                .start()

            // Upload to Firebase Storage
            uploadProfileImage(selectedImageUri!!)

            dialog.dismiss()
        }

        dialogBinding.btnConfirmNo.setOnClickListener {
            dialog.dismiss()
            // Optionally re-open image picker
            openImagePicker()
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun uploadProfileImage(imageUri: Uri) {
        val currentUser = auth.currentUser ?: return

        binding.progressBar?.visibility = android.view.View.VISIBLE

        // Create unique filename
        val filename = "profile_images/${currentUser.uid}_${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child(filename)

        storageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                // Get download URL
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Update Firebase Auth profile
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setPhotoUri(downloadUri)
                        .build()

                    currentUser.updateProfile(profileUpdates)
                        .addOnSuccessListener {
                            binding.progressBar?.visibility = android.view.View.GONE
                            Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            binding.progressBar?.visibility = android.view.View.GONE
                            Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar?.visibility = android.view.View.GONE
                Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditProfileDialog() {
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Pre-fill with current data
        dialogBinding.etEditName.setText(binding.userName.text)
        dialogBinding.etEditBio.setText(binding.userBio.text)
        dialogBinding.etEditPhone.setText(binding.userPhone.text)

        // Save button click listener
        dialogBinding.btnSave.setOnClickListener {
            val newName = dialogBinding.etEditName.text.toString().trim()
            val newBio = dialogBinding.etEditBio.text.toString().trim()
            val newPhone = dialogBinding.etEditPhone.text.toString().trim()

            // Validation
            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            updateProfile(newName, newBio, newPhone)
            dialog.dismiss()
        }

        // Cancel button click listener
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun updateProfile(name: String, bio: String, phone: String) {
        val currentUser = auth.currentUser ?: return

        // Update display name in Firebase Auth
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()

        currentUser.updateProfile(profileUpdates)
            .addOnSuccessListener {
                // Save additional info in SharedPreferences
                val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                sharedPref.edit().apply {
                    putString("username", name)
                    putString("userBio", bio)
                    putString("userPhone", phone)
                    apply()
                }

                // Update UI immediately with animation
                animateTextChange(binding.userName, name)
                animateTextChange(binding.userBio, bio)
                animateTextChange(binding.userPhone, phone)

                // Also update the user object in binding
                binding.user = UserProfile(
                    name = name,
                    email = currentUser.email ?: "No email",
                    phone = phone,
                    bio = bio,
                    profileImageUrl = currentUser.photoUrl?.toString() ?: ""
                )

                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun animateTextChange(textView: android.widget.TextView, newText: String) {
        textView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                textView.text = newText
                textView.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun changePassword() {
        val currentUser = auth.currentUser
        val email = currentUser?.email

        if (email.isNullOrEmpty()) {
            Toast.makeText(this, "Email not available", Toast.LENGTH_SHORT).show()
            return
        }

        // Send password reset email
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Password reset email sent to $email",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to send reset email: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showLogoutDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)
        val dialogBinding = com.nigdroid.journal.databinding.DialogConfirmSignoutBinding.inflate(layoutInflater)

        dialogBinding.btnDelete.setOnClickListener {
            performLogout()
            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener {
            Toast.makeText(this, "Logout Cancelled", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showDeleteAccountDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)
        val dialogBinding = com.nigdroid.journal.databinding.DialogConfirmDeleteBinding.inflate(layoutInflater)

        dialogBinding.btnDelete.setOnClickListener {
            performDeleteAccount()
            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener {
            Toast.makeText(this, "Account deletion cancelled", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun performLogout() {
        // Sign out from Firebase
        auth.signOut()

        // Clear SharedPreferences
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        sharedPref.edit().clear().apply()

        // Navigate to login screen
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }

    private fun performDeleteAccount() {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar?.visibility = android.view.View.VISIBLE

        // Delete user data from Firestore
        val userId = currentUser.uid
        deleteUserDataFromFirestore(userId) { success ->
            if (success) {
                // Delete Firebase Auth account
                currentUser.delete()
                    .addOnSuccessListener {
                        binding.progressBar?.visibility = android.view.View.GONE

                        // Clear SharedPreferences
                        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                        sharedPref.edit().clear().apply()

                        Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()

                        // Navigate to login screen
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar?.visibility = android.view.View.GONE
                        Toast.makeText(
                            this,
                            "Failed to delete account: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            } else {
                binding.progressBar?.visibility = android.view.View.GONE
                Toast.makeText(this, "Failed to delete user data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteUserDataFromFirestore(userId: String, callback: (Boolean) -> Unit) {
        // Delete user's journals
        db.collection("Journal")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    doc.reference.delete()
                }
            }

        // Delete user's text notes
        db.collection("TextNotes")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    doc.reference.delete()
                }
            }

        // Delete user's todos
        db.collection("TodoItems")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    doc.reference.delete()
                }
            }

        // Delete user's audio notes
        db.collection("AudioNotes")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    doc.reference.delete()
                }
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }
}