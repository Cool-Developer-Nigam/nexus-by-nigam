package com.nigdroid.journal

import com.google.firebase.firestore.PropertyName

data class TextNote(
    var id: String = "",
    var title: String = "",
    var content: String = "", // Rich text with HTML formatting
    var textColor: String = "#000000", // Text color in hex
    var backgroundColor: String = "#FFFFFF", // Note background color
    var userId: String = "",
    var username: String = "",
    var timeAdded: Long = System.currentTimeMillis(),
    var timeModified: Long = System.currentTimeMillis(),

    @get:PropertyName("isPinned")
    @set:PropertyName("isPinned")
    var isPinned: Boolean = false
)