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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.nigdroid.journal.databinding.ActivityAddJournalBinding
import com.nigdroid.journal.databinding.DialogConfirmImageBinding
import com.nigdroid.journal.utils.TextFormattingHelper
import com.nigdroid.journal.utils.BackgroundColorHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log

class AddJournalActivity : AppCompatActivity() {
    lateinit var binding: ActivityAddJournalBinding
    lateinit var toolbarBinding: com.nigdroid.journal.databinding.ToolbarAddJournalBinding

    private lateinit var toolbar: Toolbar
    var currentUserId: String = ""
    var currentUserName: String = ""

    var db: FirebaseFirestore = FirebaseFirestore.getInstance()
    lateinit var storageReference: StorageReference
    var collectionReference: CollectionReference = db.collection("Journal")

    var imageUri: Uri? = null
    lateinit var auth: FirebaseAuth
    lateinit var user: FirebaseUser

    private var titleFormattingHelper: TextFormattingHelper? = null
    private var thoughtsFormattingHelper: TextFormattingHelper? = null
    private var backgroundColorHelper: BackgroundColorHelper? = null

    private var isPinned = false

    companion object {
        private const val TAG = "AddJournalActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_journal)

        setupCustomToolbar()
        setupThoughtsEditTextScrolling()
        setupFormatting()

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
                pickImage()
            }

            displayImage.setOnClickListener {
                pickImage()
            }

            PostSaveJournalButton.setOnClickListener {
                saveJournal()
            }
        }
    }

    private fun setupCustomToolbar() {
        toolbar = binding.toolbarLayout.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.toolbarLayout.apply {
            save.setOnClickListener {
                saveJournal()
            }

            backBtn.setOnClickListener {
                startActivity(Intent(this@AddJournalActivity, JournalListActivity::class.java))
                finish()
            }

            btnPin.setOnClickListener {
                isPinned = !isPinned
                updatePinIcon()
            }
        }

        updatePinIcon()
    }

    private fun updatePinIcon() {
        binding.toolbarLayout.btnPin.setImageResource(
            if (isPinned) R.drawable.ic_pin else R.drawable.ic_pin_outline
        )
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, 1)
    }

    private fun setupFormatting() {
        binding.titleFormattingToolbar?.let { toolbar ->
            titleFormattingHelper = TextFormattingHelper(
                editText = binding.etPostTitle,
                boldButton = toolbar.findViewById(R.id.btnTitleBold),
                italicButton = toolbar.findViewById(R.id.btnTitleItalic),
                underlineButton = toolbar.findViewById(R.id.btnTitleUnderline),
                textColorButton = toolbar.findViewById(R.id.btnTitleTextColor)
            )
        }

        binding.thoughtsFormattingToolbar?.let { toolbar ->
            thoughtsFormattingHelper = TextFormattingHelper(
                editText = binding.etThoughts,
                boldButton = toolbar.findViewById(R.id.btnThoughtsBold),
                italicButton = toolbar.findViewById(R.id.btnThoughtsItalic),
                underlineButton = toolbar.findViewById(R.id.btnThoughtsUnderline),
                textColorButton = toolbar.findViewById(R.id.btnThoughtsTextColor)
            )
        }

        binding.journalContainer?.let { container ->
            binding.btnBackgroundColor?.let { button ->
                backgroundColorHelper = BackgroundColorHelper(
                    targetView = container,
                    colorButton = button
                )
            }
        }

        binding.etPostTitle.setOnFocusChangeListener { _, hasFocus ->
            binding.titleFormattingToolbar?.visibility = if (hasFocus) View.VISIBLE else View.GONE
            binding.thoughtsFormattingToolbar?.visibility = View.GONE
        }

        binding.etThoughts.setOnFocusChangeListener { _, hasFocus ->
            binding.thoughtsFormattingToolbar?.visibility = if (hasFocus) View.VISIBLE else View.GONE
            binding.titleFormattingToolbar?.visibility = View.GONE
        }
    }

    private fun setupThoughtsEditTextScrolling() {
        binding.etThoughts.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                scrollToCursorPosition()
            }
        })

        binding.etThoughts.setOnTouchListener { v, event ->
            handleEditTextTouch(v, event)
        }
    }

    private fun scrollToCursorPosition() {
        val layout = binding.etThoughts.layout
        if (layout != null) {
            val cursorLine = layout.getLineForOffset(binding.etThoughts.selectionStart)
            val scrollY = layout.getLineTop(cursorLine) - binding.etThoughts.height / 2
            binding.etThoughts.scrollTo(0, Math.max(0, scrollY))
        }
    }

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
        val title = if (titleFormattingHelper != null) {
            android.text.Html.toHtml(
                binding.etPostTitle.text as android.text.Spanned,
                android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE
            )
        } else {
            binding.etPostTitle.text.toString().trim()
        }

        val thoughts = if (thoughtsFormattingHelper != null) {
            android.text.Html.toHtml(
                binding.etThoughts.text as android.text.Spanned,
                android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE
            )
        } else {
            binding.etThoughts.text.toString().trim()
        }

        if (TextUtils.isEmpty(title.trim())) {
            binding.etPostTitle.error = "Title is required"
            return
        }

        if (TextUtils.isEmpty(thoughts.trim())) {
            binding.etThoughts.error = "Thoughts are required"
            return
        }

        if (imageUri == null) {
            Toast.makeText(this, "Please select an image", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        uploadImageAndSaveJournal(title, thoughts)
    }

    private fun uploadImageAndSaveJournal(title: String, thoughts: String) {
        val filepath: StorageReference = storageReference
            .child("journal_images")
            .child("my_image_${Timestamp.now().seconds}")

        filepath.putFile(imageUri!!)
            .addOnSuccessListener {
                filepath.downloadUrl.addOnSuccessListener { uri ->
                    val imageUrl: String = uri.toString()
                    createAndSaveJournal(title, thoughts, imageUrl)
                }
            }
            .addOnFailureListener { e ->
                handleImageUploadError(e)
            }
    }

    private fun handleImageUploadError(exception: Exception) {
        binding.progressBar.visibility = View.INVISIBLE
        when (exception) {
            is FirebaseFirestoreException -> {
                when (exception.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE -> {
                        Log.w(TAG, "Network unavailable for image upload", exception)
                        Toast.makeText(
                            this,
                            "You're offline. Image upload will be retried when online.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        Log.e(TAG, "Error uploading image: ${exception.message}", exception)
                        Toast.makeText(
                            this,
                            "Failed to upload image: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            else -> {
                Log.e(TAG, "Failed to upload image", exception)
                Toast.makeText(this, "Failed to upload image: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createAndSaveJournal(title: String, thoughts: String, imageUrl: String) {
        val date = Date()
        val sdf = SimpleDateFormat("HH:mm, EEE MMM dd yyyy", Locale.getDefault())
        val timestamp: String = sdf.format(date)

        val journal = Journal(
            title = title,
            thoughts = thoughts,
            imageUrl = imageUrl,
            userId = currentUserId,
            timeAdded = timestamp,
            username = currentUserName,
            textColor = thoughtsFormattingHelper?.textColor ?: "#000000",
            backgroundColor = backgroundColorHelper?.backgroundColor ?: "#FFFFFF",
            isPinned = isPinned
        )

        collectionReference.add(journal)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.INVISIBLE
                Log.d(TAG, "Journal saved successfully")
                Toast.makeText(this, "Journal saved successfully", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, JournalListActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                handleSaveError(e)
            }
    }

    private fun handleSaveError(exception: Exception) {
        binding.progressBar.visibility = View.INVISIBLE
        when (exception) {
            is FirebaseFirestoreException -> {
                when (exception.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE -> {
                        Log.w(TAG, "Network unavailable, save queued", exception)
                        Toast.makeText(
                            this,
                            "You're offline. Changes will sync when online.",
                            Toast.LENGTH_LONG
                        ).show()
                        startActivity(Intent(this, JournalListActivity::class.java))
                        finish()
                    }
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION -> {
                        Log.e(TAG, "Index required", exception)
                        Toast.makeText(
                            this,
                            "Database needs optimization. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> {
                        Log.e(TAG, "Error saving journal: ${exception.message}", exception)
                        Toast.makeText(
                            this,
                            "Failed to save journal: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            else -> {
                Log.e(TAG, "Unexpected error saving journal", exception)
                Toast.makeText(
                    this,
                    "Error: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
        val dialogBinding = DialogConfirmImageBinding.inflate(layoutInflater)

        dialogBinding.btnConfirmYes.setOnClickListener {
            imageUri = selectedUri
            binding.displayImage.setImageURI(imageUri)
            binding.displayImage.visibility = View.VISIBLE
            binding.postCameraButton.visibility = View.GONE
            dialog.dismiss()
        }

        dialogBinding.btnConfirmNo.setOnClickListener {
            dialog.dismiss()
            pickImage()
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun onStart() {
        super.onStart()
        user = auth.currentUser!!
    }
}