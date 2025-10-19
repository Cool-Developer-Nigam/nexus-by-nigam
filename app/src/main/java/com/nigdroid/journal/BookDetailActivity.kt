package com.nigdroid.journal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class BookDetailActivity : AppCompatActivity() {

    private lateinit var item: BookModel
    private lateinit var picMain: ImageView
    private lateinit var titleTxt: TextView
    private lateinit var descriptionTxt: TextView
    private lateinit var ratingTxt: TextView
    private lateinit var fileSizeTxt: TextView
    private lateinit var priceTxt: TextView
    private lateinit var openBookBtn: View
    private lateinit var backBtn: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)

        initViews()
        loadBookData()
    }

    private fun initViews() {
        picMain = findViewById(R.id.picMain)
        titleTxt = findViewById(R.id.titleTxt)
        descriptionTxt = findViewById(R.id.descriptionTxt)
        ratingTxt = findViewById(R.id.ratingTxt)
        fileSizeTxt = findViewById(R.id.numberItemTxt)
        priceTxt = findViewById(R.id.priceTxt)
        openBookBtn = findViewById(R.id.addToCartBtn)
        backBtn = findViewById(R.id.backBtn)

        openBookBtn.setOnClickListener {
            openPdfBook()
        }

        backBtn.setOnClickListener {
            finish()
        }
    }

    private fun loadBookData() {
        // Get the book object from intent
        item = intent.getSerializableExtra("object") as BookModel

        // Load book image
        Glide.with(this@BookDetailActivity)
            .load(item.picUrl)
            .centerCrop()
            .placeholder(R.drawable.ic_launcher_background) // Add a placeholder
            .into(picMain)

        // Set book details
        titleTxt.text = item.title
        descriptionTxt.text = item.description
        ratingTxt.text = item.rating.toString()
        fileSizeTxt.text = item.fileSize
        priceTxt.text = item.fileSize
    }

    private fun openPdfBook() {
        if (item.pdfUrl.isNotEmpty()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(item.pdfUrl), "application/pdf")
                intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY

                val chooser = Intent.createChooser(intent, "Open PDF with")
                startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "No PDF viewer found. Please install one.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(this, "PDF URL not available", Toast.LENGTH_SHORT).show()
        }
    }
}