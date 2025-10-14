package com.nigdroid.journal

data class Journal(
    val title: String = "",
    val thoughts: String = "", // Can now contain HTML formatting
    val imageUrl: String = "",
    val userId: String = "",
    val timeAdded: String = "",
    val username: String = "",
    val textColor: String = "#000000", // Text color for formatting
    val backgroundColor: String = "#FFFFFF", // Background color
    val isPinned: Boolean = false // Pinning functionality
)