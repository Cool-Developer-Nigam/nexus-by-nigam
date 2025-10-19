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
    private lateinit var authorTxt: TextView
    private lateinit var descriptionTxt: TextView
    private lateinit var ratingTxt: TextView
    private lateinit var fileSizeTxt: TextView
    private lateinit var openBookBtn: View
    private lateinit var backBtn: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)

        picMain = findViewById(R.id.picMain)
        titleTxt = findViewById(R.id.titleTxt)
        authorTxt = findViewById(R.id.text13)
        descriptionTxt = findViewById(R.id.descriptionTxt)
        ratingTxt = findViewById(R.id.ratingTxt)
        fileSizeTxt = findViewById(R.id.numberItemTxt)
        openBookBtn = findViewById(R.id.addToCartBtn)
        backBtn = findViewById(R.id.backBtn)

        bundle()
    }

    private fun bundle() {
        item = intent.getSerializableExtra("object") as BookModel

        Glide.with(this@BookDetailActivity)
            .load(item.picUrl)
            .into(picMain)

        titleTxt.text = item.title
        authorTxt.text = "by ${item.author}"
        descriptionTxt.text = item.description
        ratingTxt.text = item.rating.toString()
        fileSizeTxt.text = item.fileSize

        findViewById<TextView>(R.id.text8).visibility = View.GONE
        findViewById<View>(R.id.smallBtn).visibility = View.GONE
        findViewById<View>(R.id.mediumBtn).visibility = View.GONE
        findViewById<View>(R.id.largeBtn).visibility = View.GONE

        findViewById<TextView>(R.id.text9).visibility = View.GONE
        findViewById<View>(R.id.minusCart).visibility = View.GONE
        findViewById<View>(R.id.plusCart).visibility = View.GONE

        findViewById<TextView>(R.id.text13).text = "Open Book"
        findViewById<TextView>(R.id.priceTxt).text = item.fileSize

        openBookBtn.setOnClickListener {
            openPdfBook()
        }

        backBtn.setOnClickListener {
            finish()
        }
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
                Toast.makeText(this, "No PDF viewer found. Please install one.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "PDF URL not available", Toast.LENGTH_SHORT).show()
        }
    }
}
