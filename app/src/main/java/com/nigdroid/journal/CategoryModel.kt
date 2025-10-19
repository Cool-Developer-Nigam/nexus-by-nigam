package com.nigdroid.journal

data class CategoryModel(
    val id: Int = 0,
    val title: String = ""
)

data class BookModel(
    val categoryId: String = "",
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val extra: String = "",
    val picUrl: String = "",
    val rating: Double = 0.0,
    val fileSize: String = "",
    val pdfUrl: String = ""
) : java.io.Serializable