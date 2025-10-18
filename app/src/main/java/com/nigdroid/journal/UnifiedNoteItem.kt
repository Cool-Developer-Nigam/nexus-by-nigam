package com.nigdroid.journal

sealed class UnifiedNoteItem {
    abstract val id: String
    abstract val timeAdded: Long
    abstract val isPinned: Boolean
    abstract val userId: String

    data class JournalItem(
        val journal: Journal,
        override val id: String
    ) : UnifiedNoteItem() {
        override val timeAdded: Long = journal.timeAdded.toLongOrNull() ?: System.currentTimeMillis()
        override val isPinned: Boolean = journal.isPinned
        override val userId: String = journal.userId
    }

    data class TextNoteItem(
        val textNote: TextNote
    ) : UnifiedNoteItem() {
        override val id: String = textNote.id
        override val timeAdded: Long = textNote.timeModified
        override val isPinned: Boolean = textNote.isPinned
        override val userId: String = textNote.userId
    }

    data class TodoItemWrapper(
        val todoItem: TodoItem
    ) : UnifiedNoteItem() {
        override val id: String = todoItem.id
        override val timeAdded: Long = todoItem.timeModified
        override val isPinned: Boolean = todoItem.isPinned
        override val userId: String = todoItem.userId
    }

    data class AudioNoteItem(
        val audioNote: AudioNote
    ) : UnifiedNoteItem() {
        override val id: String = audioNote.id
        override val timeAdded: Long = audioNote.timeModified
        override val isPinned: Boolean = audioNote.isPinned
        override val userId: String = audioNote.userId
    }
}