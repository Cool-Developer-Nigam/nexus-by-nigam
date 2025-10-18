package com.nigdroid.journal

import com.google.firebase.firestore.PropertyName

data class TodoItem(
    val id: String = "",
    val title: String = "",
    val items: MutableList<ChecklistItem> = mutableListOf(),
    val userId: String = "",
    val username: String = "",
    val timeAdded: Long = System.currentTimeMillis(),
    val timeModified: Long = System.currentTimeMillis(),
    val backgroundColor: String = "#FFFFFF",

    @get:PropertyName("isPinned")
    @set:PropertyName("isPinned")
    var isPinned: Boolean = false
)

data class ChecklistItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var text: String = "",
    var isChecked: Boolean = false
)