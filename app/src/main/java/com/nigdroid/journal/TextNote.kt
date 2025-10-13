package com.nigdroid.journal

data class TextNote(
    val id: String = "",
    val title: String = "",
    val content: String = "", // Rich text with HTML formatting
    val textColor: String = "#000000", // Text color in hex
    val backgroundColor: String = "#FFFFFF", // Note background color
    val userId: String = "",
    val username: String = "",
    val timeAdded: Long = System.currentTimeMillis(),
    val timeModified: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)