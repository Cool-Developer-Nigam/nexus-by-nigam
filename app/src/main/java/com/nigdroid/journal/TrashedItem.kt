package com.nigdroid.journal

// Data class for trashed items
data class TrashedItem(
    val id: String = "",
    val originalId: String = "",
    val userId: String = "",
    val type: String = "", // "journal", "textNote", "todo", "audio"
    val trashedAt: Long = 0L,
    val originalData: Map<String, Any> = mapOf()
) {
    fun calculateDaysRemaining(): Int {
        val currentTime = System.currentTimeMillis()
        val elapsedDays = ((currentTime - trashedAt) / (24 * 60 * 60 * 1000)).toInt()
        return maxOf(0, 7 - elapsedDays)
    }
}