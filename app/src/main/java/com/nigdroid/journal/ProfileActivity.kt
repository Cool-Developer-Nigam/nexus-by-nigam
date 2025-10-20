package com.nigdroid.journal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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

        // Create UserProfile object
        val user = UserProfile(
            name = currentUser.displayName ?: "Unknown User",
            email = currentUser.email ?: "No email",
            phone = currentUser.phoneNumber ?: "Not provided",
            bio = "Software Developer | Passionate about coding", // Default bio
            profileImageUrl = currentUser.photoUrl?.toString() ?: ""
        )

        // Set user data to binding
        binding.user = user

        // Load profile image with Glide
        if (user.profileImageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(user.profileImageUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(binding.profileImage)
        }

        // Try to load additional user info from SharedPreferences
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val savedBio = sharedPref.getString("userBio", user.bio)
        val savedPhone = sharedPref.getString("userPhone", user.phone)

        binding.userBio.text = savedBio
        binding.userPhone.text = savedPhone
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

            // Display selected image in profile
            Glide.with(this)
                .load(selectedImageUri)
                .centerCrop()
                .into(binding.profileImage)

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
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)

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
                dialogBinding.etEditName.error = "Name cannot be empty"
                return@setOnClickListener
            }

            updateProfile(newName, newBio, newPhone)
            dialog.dismiss()
        }

        // Cancel button click listener
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(dialogBinding.root)
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

                // Update UI
                binding.userName.text = name
                binding.userBio.text = bio
                binding.userPhone.text = phone

                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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