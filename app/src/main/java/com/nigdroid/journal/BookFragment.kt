package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.*
import com.nigdroid.journal.databinding.FragmentBookBinding

class BookFragment : Fragment() {

    private var _binding: FragmentBookBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: BookCategoryAdapter
    private lateinit var popularAdapter: PopularBookAdapter

    private val database = FirebaseDatabase.getInstance()
    private val categories = mutableListOf<CategoryModel>()
    private val popularBooks = mutableListOf<BookModel>()
    private val allBooks = mutableListOf<BookModel>()

    private var isExpanded = false
    private val collapsedItemCount = 4
    private var isFragmentAlive = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookBinding.inflate(inflater, container, false)
        isFragmentAlive = true
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.chatbot.setOnClickListener {
            if (isAdded && isFragmentAlive) {
                startActivity(Intent(requireContext(), GeminiActivity::class.java))
            }
        }

        setupRecyclerViews()
        setupSearchFunctionality()
        setupSeeAllFunctionality()
        loadCategoriesFromRealtimeDatabase()
        loadAllBooksFromRealtimeDatabase()
    }

    private fun setupRecyclerViews() {
        categoryAdapter = BookCategoryAdapter(categories)
        binding.recyclerView1.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }

        popularAdapter = PopularBookAdapter(popularBooks)
        binding.recyclerView2.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = popularAdapter
        }
    }

    private fun setupSearchFunctionality() {
        binding.edtTxtSrch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isAdded && isFragmentAlive) {
                    filterBooks(s.toString())
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterBooks(query: String) {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        if (query.isEmpty()) {
            // Show all books or collapsed view based on current state
            updatePopularBooksDisplay()
        } else {
            // Filter ALL books (not just popular) based on search query
            val filteredBooks = allBooks.filter { book ->
                book.title.contains(query, ignoreCase = true) ||
                        book.author.contains(query, ignoreCase = true) ||
                        book.description.contains(query, ignoreCase = true) ||
                        book.extra.contains(query, ignoreCase = true)
            }

            popularBooks.clear()
            popularBooks.addAll(filteredBooks)
            popularAdapter.notifyDataSetChanged()

            if (filteredBooks.isEmpty()) {
                context?.let {
                    Toast.makeText(it, "No books found matching \"$query\"", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSeeAllFunctionality() {
        binding.txtSeeAll.setOnClickListener {
            if (!isAdded || !isFragmentAlive || _binding == null) return@setOnClickListener

            isExpanded = !isExpanded

            // Clear search when toggling see all
            if (isExpanded && binding.edtTxtSrch.text.toString().isNotEmpty()) {
                binding.edtTxtSrch.setText("")
            }

            updatePopularBooksDisplay()
            animateArrow()

            // Update text
            binding.txtSeeAll.text = if (isExpanded) "Show less" else "See all"
        }

        binding.ivSeeAllArrow.setOnClickListener {
            if (!isAdded || !isFragmentAlive || _binding == null) return@setOnClickListener

            isExpanded = !isExpanded

            // Clear search when toggling see all
            if (isExpanded && binding.edtTxtSrch.text.toString().isNotEmpty()) {
                binding.edtTxtSrch.setText("")
            }

            updatePopularBooksDisplay()
            animateArrow()

            // Update text
            binding.txtSeeAll.text = if (isExpanded) "Show less" else "See all"
        }
    }

    private fun animateArrow() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        val rotation = if (isExpanded) 180f else 0f
        binding.ivSeeAllArrow.animate()
            .rotation(rotation)
            .setDuration(200)
            .start()
    }

    private fun updatePopularBooksDisplay() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        popularBooks.clear()

        if (isExpanded) {
            // Show ALL books from all categories in reverse order
            popularBooks.addAll(allBooks.reversed())
        } else {
            // Show only first few items (collapsed view)
            popularBooks.addAll(allBooks.take(collapsedItemCount))
        }

        popularAdapter.notifyDataSetChanged()
    }

    private fun loadCategoriesFromRealtimeDatabase() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        binding.progressBar2.visibility = View.VISIBLE

        android.util.Log.d("BookFragment", "Loading categories from Realtime Database...")

        database.reference.child("Category")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || !isFragmentAlive || _binding == null) return

                    android.util.Log.d("BookFragment", "Query successful. Children count: ${snapshot.childrenCount}")

                    if (!snapshot.exists()) {
                        android.util.Log.w("BookFragment", "No data in Category node!")
                        context?.let {
                            Toast.makeText(it, "No categories found. Please check Firebase.", Toast.LENGTH_LONG).show()
                        }
                        binding.progressBar2.visibility = View.GONE
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
                    binding.progressBar2.visibility = View.GONE

                    android.util.Log.d("BookFragment", "Total categories loaded: ${categories.size}")

                    if (categories.isEmpty()) {
                        context?.let {
                            Toast.makeText(it, "No valid categories found", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!isAdded || !isFragmentAlive || _binding == null) return

                    android.util.Log.e("BookFragment", "Error loading categories: ${error.message}", error.toException())
                    binding.progressBar2.visibility = View.GONE
                    context?.let {
                        Toast.makeText(it, "Error loading categories: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })
    }

    private fun loadAllBooksFromRealtimeDatabase() {
        if (!isAdded || !isFragmentAlive || _binding == null) return

        binding.progressBar3.visibility = View.VISIBLE

        android.util.Log.d("BookFragment", "Loading all books from Realtime Database...")

        // Load from Popular node first
        database.reference.child("Popular")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(popularSnapshot: DataSnapshot) {
                    if (!isAdded || !isFragmentAlive || _binding == null) return

                    android.util.Log.d("BookFragment", "Popular books count: ${popularSnapshot.childrenCount}")

                    allBooks.clear()

                    // Add popular books
                    for (bookSnapshot in popularSnapshot.children) {
                        try {
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
                            allBooks.add(book)
                        } catch (e: Exception) {
                            android.util.Log.e("BookFragment", "Error parsing popular book", e)
                        }
                    }

                    // Now load all items from Items node
                    database.reference.child("Items")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(itemsSnapshot: DataSnapshot) {
                                if (!isAdded || !isFragmentAlive || _binding == null) return

                                android.util.Log.d("BookFragment", "Items books count: ${itemsSnapshot.childrenCount}")

                                for (bookSnapshot in itemsSnapshot.children) {
                                    try {
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

                                        // Avoid duplicates by checking if book already exists
                                        if (!allBooks.any { it.title == book.title && it.author == book.author }) {
                                            allBooks.add(book)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("BookFragment", "Error parsing item book", e)
                                    }
                                }

                                updatePopularBooksDisplay()
                                binding.progressBar3.visibility = View.GONE

                                android.util.Log.d("BookFragment", "Total books loaded: ${allBooks.size}")

                                if (allBooks.isEmpty()) {
                                    context?.let {
                                        Toast.makeText(it, "No books found in database", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                if (!isAdded || !isFragmentAlive || _binding == null) return

                                android.util.Log.e("BookFragment", "Error loading items: ${error.message}", error.toException())
                                binding.progressBar3.visibility = View.GONE
                                context?.let {
                                    Toast.makeText(it, "Error loading books: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!isAdded || !isFragmentAlive || _binding == null) return

                    android.util.Log.e("BookFragment", "Error loading popular books: ${error.message}", error.toException())
                    binding.progressBar3.visibility = View.GONE
                    context?.let {
                        Toast.makeText(it, "Error loading books: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentAlive = false
        _binding = null
    }
}