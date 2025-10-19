package com.nigdroid.journal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class BookFragment : Fragment() {

    private lateinit var categoryRecyclerView: RecyclerView
    private lateinit var popularRecyclerView: RecyclerView
    private lateinit var progressBar1: ProgressBar
    private lateinit var progressBar2: ProgressBar
    private lateinit var searchBooks: EditText

    private lateinit var categoryAdapter: BookCategoryAdapter
    private lateinit var popularAdapter: PopularBookAdapter

    private val database = FirebaseDatabase.getInstance()
    private val categories = mutableListOf<CategoryModel>()
    private val popularBooks = mutableListOf<BookModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_book, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryRecyclerView = view.findViewById(R.id.recyclerView1)
        popularRecyclerView = view.findViewById(R.id.recyclerView2)
        progressBar1 = view.findViewById(R.id.progressBar2)
        progressBar2 = view.findViewById(R.id.progressBar3)
        searchBooks = view.findViewById(R.id.edt_txt_srch)

        setupRecyclerViews()
        loadCategoriesFromRealtimeDatabase()
        loadPopularBooksFromRealtimeDatabase()
    }

    private fun setupRecyclerViews() {
        categoryAdapter = BookCategoryAdapter(categories)
        categoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }

        popularAdapter = PopularBookAdapter(popularBooks)
        popularRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = popularAdapter
        }
    }

    private fun loadCategoriesFromRealtimeDatabase() {
        progressBar1.visibility = View.VISIBLE

        android.util.Log.d("BookFragment", "Loading categories from Realtime Database...")

        database.reference.child("Category")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    android.util.Log.d("BookFragment", "Query successful. Children count: ${snapshot.childrenCount}")

                    if (!snapshot.exists()) {
                        android.util.Log.w("BookFragment", "No data in Category node!")
                        Toast.makeText(context, "No categories found. Please check Firebase.", Toast.LENGTH_LONG).show()
                        progressBar1.visibility = View.GONE
                        return
                    }

                    categories.clear()
                    for (categorySnapshot in snapshot.children) {
                        try {
                            android.util.Log.d("BookFragment", "Category key: ${categorySnapshot.key}, Data: ${categorySnapshot.value}")

                            val id = categorySnapshot.child("id").getValue(Int::class.java) ?: 0
                            val title = categorySnapshot.child("title").getValue(String::class.java) ?: ""

                            if (title.isNotEmpty()) {
                                val category = CategoryModel(
                                    id = id,
                                    title = title
                                )
                                categories.add(category)
                                android.util.Log.d("BookFragment", "Added category: ID=$id, Title=$title")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BookFragment", "Error parsing category", e)
                        }
                    }

                    categoryAdapter.notifyDataSetChanged()
                    progressBar1.visibility = View.GONE

                    android.util.Log.d("BookFragment", "Total categories loaded: ${categories.size}")

                    if (categories.isEmpty()) {
                        Toast.makeText(context, "No valid categories found", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("BookFragment", "Error loading categories: ${error.message}", error.toException())
                    progressBar1.visibility = View.GONE
                    Toast.makeText(context, "Error loading categories: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun loadPopularBooksFromRealtimeDatabase() {
        progressBar2.visibility = View.VISIBLE

        android.util.Log.d("BookFragment", "Loading popular books from Realtime Database...")

        database.reference.child("Popular")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    android.util.Log.d("BookFragment", "Query successful. Books count: ${snapshot.childrenCount}")

                    if (!snapshot.exists()) {
                        android.util.Log.w("BookFragment", "No data in Popular node!")
                        Toast.makeText(context, "No popular books found. Please check Firebase.", Toast.LENGTH_LONG).show()
                        progressBar2.visibility = View.GONE
                        return
                    }

                    popularBooks.clear()
                    for (bookSnapshot in snapshot.children) {
                        try {
                            android.util.Log.d("BookFragment", "Book key: ${bookSnapshot.key}, Data: ${bookSnapshot.value}")

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
                            popularBooks.add(book)
                            android.util.Log.d("BookFragment", "Added book: ${book.title}")
                        } catch (e: Exception) {
                            android.util.Log.e("BookFragment", "Error parsing book", e)
                        }
                    }

                    popularAdapter.notifyDataSetChanged()
                    progressBar2.visibility = View.GONE

                    android.util.Log.d("BookFragment", "Total books loaded: ${popularBooks.size}")
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("BookFragment", "Error loading books: ${error.message}", error.toException())
                    progressBar2.visibility = View.GONE
                    Toast.makeText(context, "Error loading books: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}