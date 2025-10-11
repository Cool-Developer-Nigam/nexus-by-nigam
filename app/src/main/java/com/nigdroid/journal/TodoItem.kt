package com.nigdroid.journal

data class TodoItem(
    val id: String = "",
    val title: String = "",
    val items: MutableList<ChecklistItem> = mutableListOf(),
    val userId: String = "",
    val username: String = "",
    val timeAdded: Long = System.currentTimeMillis(),
    val timeModified: Long = System.currentTimeMillis(),
    val backgroundColor: String = "#FFFFFF",
    val isPinned: Boolean = false
)

data class ChecklistItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var text: String = "",
    var isChecked: Boolean = false
)