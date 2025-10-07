package com.nigdroid.journal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.nigdroid.journal.databinding.ActivityAddJournalBinding
import com.nigdroid.journal.databinding.DialogConfirmImageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddJournalActivity : AppCompatActivity() {
    lateinit var binding: ActivityAddJournalBinding

    private lateinit var toolbar: Toolbar
    // Credential
    var currentUserId: String = ""
    var currentUserName: String = ""

    // Firebase firestore
    var db: FirebaseFirestore = FirebaseFirestore.getInstance()
    lateinit var storageReference: StorageReference
    var collectionReference: CollectionReference = db.collection("Journal")

    var imageUri: Uri? = null

    // Firebase authentication
    lateinit var auth: FirebaseAuth
    lateinit var user: FirebaseUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_journal)
        setupCustomToolbar()

        // Setup thoughts EditText scrolling behavior
        setupThoughtsEditTextScrolling()

        storageReference = FirebaseStorage.getInstance().reference
        auth = Firebase.auth

        binding.apply {
            progressBar.visibility = View.INVISIBLE

            if (JournalUser.instance != null) {
                val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                currentUserName = sharedPref.getString("username", "Unknown") ?: "Unknown"
                currentUserId = auth.currentUser!!.uid

                postUsernameTv.text = currentUserName
                val date = Date()
                val sdf = SimpleDateFormat("HH:mm, EEE MMM dd yyyy", Locale.getDefault())
                tvPostDate.text = sdf.format(date)
            }

            postCameraButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "image/*"
                startActivityForResult(intent, 1)
            }

            // Also allow clicking on the displayed image to change it
            displayImage.setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "image/*"
                startActivityForResult(intent, 1)
            }

            PostSaveJournalButton.setOnClickListener {
                saveJournal()
            }

        }
    }


    private fun setupCustomToolbar() {
        toolbar = binding.toolbarLayout.toolbar
        setSupportActionBar(toolbar)

        // Hide default title
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.toolbarLayout.save.setOnClickListener {
            saveJournal()
        }
        binding.toolbarLayout.backBtn.setOnClickListener {
            startActivity(Intent(this, JournalListActivity::class.java))
            finish()
        }
    }

    /**
     * Sets up auto-scrolling behavior for the thoughts EditText
     * Makes the EditText scroll to cursor position as the user types
     */
    private fun setupThoughtsEditTextScrolling() {
        binding.etThoughts.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                scrollToCursorPosition()
            }
        })

        // Allow EditText to scroll independently
        binding.etThoughts.setOnTouchListener { v, event ->
            handleEditTextTouch(v, event)
        }
    }

    /**
     * Scrolls the thoughts EditText to show the current cursor position
     * Centers the cursor line in the visible area
     */
    private fun scrollToCursorPosition() {
        val layout = binding.etThoughts.layout
        if (layout != null) {
            // Get the line where cursor is currently positioned
            val cursorLine = layout.getLineForOffset(binding.etThoughts.selectionStart)
            // Calculate scroll position to center the cursor line
            val scrollY = layout.getLineTop(cursorLine) - binding.etThoughts.height / 2
            // Scroll to the calculated position (ensuring it's not negative)
            binding.etThoughts.scrollTo(0, Math.max(0, scrollY))
        }
    }

    /**
     * Handles touch events for the thoughts EditText
     * Prevents parent views from intercepting scroll events
     * @return false to allow normal touch event handling
     */
    private fun handleEditTextTouch(v: View, event: MotionEvent): Boolean {
        v.parent.requestDisallowInterceptTouchEvent(true)
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_UP -> {
                v.parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return false
    }

    private fun saveJournal() {
        val title: String = binding.etPostTitle.text.toString().trim()
        val thoughts: String = binding.etThoughts.text.toString().trim()

        // Validate inputs
        if (TextUtils.isEmpty(title)) {
            binding.etPostTitle.error = "Title is required"
            return
        }

        if (TextUtils.isEmpty(thoughts)) {
            binding.etThoughts.error = "Thoughts are required"
            return
        }

        if (imageUri == null) {
            Toast.makeText(this, "Please select an image", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        // Saving the path of images in storage
        // .../journal_images/our_image.png
        val filepath: StorageReference = storageReference
            .child("journal_images")
            .child("my_image_${Timestamp.now().seconds}")

        // Uploading the image
        filepath.putFile(imageUri!!)
            .addOnSuccessListener {
                filepath.downloadUrl.addOnSuccessListener { uri ->
                    val imageUrl: String = uri.toString()

                    val date = Date()
                    val sdf = SimpleDateFormat("HH:mm, EEE MMM dd yyyy", Locale.getDefault())
                    val timestamp: String = sdf.format(date)

                    // Creating a journal object
                    val journal = Journal(
                        title,
                        thoughts,
                        imageUrl,
                        currentUserId,
                        timestamp,
                        currentUserName
                    )

                    collectionReference.add(journal)
                        .addOnSuccessListener {
                            binding.progressBar.visibility = View.INVISIBLE
                            Toast.makeText(this, "Journal saved successfully", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, JournalListActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            binding.progressBar.visibility = View.INVISIBLE
                            Toast.makeText(this,"Failed to save journal: ${e.message}",Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.INVISIBLE
                Toast.makeText(this,"Failed to upload image: ${e.message}",Toast.LENGTH_SHORT).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            val selectedUri = data.data
            if (selectedUri != null) {
                showImageConfirmationDialog(selectedUri)
            }
        }
    }

    private fun showImageConfirmationDialog(selectedUri: Uri) {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogStyle)

        // Use data binding
        val dialogBinding = DialogConfirmImageBinding.inflate(layoutInflater)

        // Show preview in the dialog if you have an ImageView in the dialog layout
        // dialogBinding.previewImage.setImageURI(selectedUri)

        dialogBinding.btnConfirmYes.setOnClickListener {
            imageUri = selectedUri

            // Show the image and hide the camera button
            binding.displayImage.setImageURI(imageUri)
            binding.displayImage.visibility = View.VISIBLE
            binding.postCameraButton.visibility = View.GONE

            dialog.dismiss()
        }

        dialogBinding.btnConfirmNo.setOnClickListener {
            dialog.dismiss()
            // Reopen gallery
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, 1)
        }

        dialog.setContentView(dialogBinding.root)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun onStart() {
        super.onStart()
        user = auth.currentUser!!
    }

    override fun onStop() {
        super.onStop()
    }
}