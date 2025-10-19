package com.nigdroid.journal

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class BookListActivity : AppCompatActivity() {

    private lateinit var listView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var categoryTxt: TextView
    private lateinit var backBtn: View

    private lateinit var adapter: BookListAdapter
    private val database = FirebaseDatabase.getInstance()
    private val books = mutableListOf<BookModel>()

    private var categoryId: String = ""
    private var title: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_list)

        listView = findViewById(R.id.listView)
        progressBar = findViewById(R.id.progressBar4)
        categoryTxt = findViewById(R.id.categoryTxt)
        backBtn = findViewById(R.id.backBtn)

        getBundle()
        initList()
    }

    private fun getBundle() {
        categoryId = intent.getStringExtra("id") ?: ""
        title = intent.getStringExtra("title") ?: ""
        categoryTxt.text = title

        android.util.Log.d("BookListActivity", "Category ID: $categoryId, Title: $title")
    }

    private fun initList() {
        backBtn.setOnClickListener { finish() }

        adapter = BookListAdapter(books)
        listView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        listView.adapter = adapter

        loadBooks()
    }

    private fun loadBooks() {
        progressBar.visibility = View.VISIBLE

        android.util.Log.d("BookListActivity", "Loading books for category: $categoryId")

        database.reference.child("Items")
            .orderByChild("categoryId")
            .equalTo(categoryId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    android.util.Log.d("BookListActivity", "Books found: ${snapshot.childrenCount}")

                    books.clear()
                    for (bookSnapshot in snapshot.children) {
                        try {
                            android.util.Log.d("BookListActivity", "Book data: ${bookSnapshot.value}")

                            val book = BookModel(
                                categoryId = bookSnapshot.child("categoryId").getValue(String::class.java) ?: "",
                                title = bookSnapshot.child("title").getValue(String::class.java) ?: "",
                                author = bookSnapshot.child("author").getValue(String::class.java) ?: "",
                                description = bookSnapshot.child("description").getValue(String::class.java) ?: "",
                                extra = bookSnapshot.child("extra").getValue(String::class.java) ?: "",
                                picUrl = bookSnapshot.child("picUrl").getValue(String::class.java) ?: "",
                                rating = bookSnapshot.child("rating").getValue(Double::class.java) ?: 0.0,
                                fileSize = bookSnapshot.child("fileSize").getValue(String::class.java) ?: "",
                                pdfUrl = bookSnapshot.child("pdfUrl").getValue(String::class.java) ?: ""
                            )
                            books.add(book)
                            android.util.Log.d("BookListActivity", "Added book: ${book.title}")
                        } catch (e: Exception) {
                            android.util.Log.e("BookListActivity", "Error parsing book", e)
                        }
                    }

                    adapter.notifyDataSetChanged()
                    progressBar.visibility = View.GONE

                    if (books.isEmpty()) {
                        Toast.makeText(this@BookListActivity, "No books found in this category", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("BookListActivity", "Error loading books: ${error.message}", error.toException())
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@BookListActivity, "Error loading books: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}