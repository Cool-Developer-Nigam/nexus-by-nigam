package com.nigdroid.journal

import com.google.firebase.firestore.PropertyName

data class AudioNote(
    val id: String = "",
    val title: String = "",
    val audioUrl: String = "", // Firebase Storage URL
    val audioDuration: Long = 0L, // in milliseconds
    val transcription: String = "", // Speech-to-text result
    val userId: String = "",
    val username: String = "",
    val timeAdded: Long = System.currentTimeMillis(),
    val timeModified: Long = System.currentTimeMillis(),

    @get:PropertyName("isPinned")
    @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    val backgroundColor: String = "#FFFFFF"
)