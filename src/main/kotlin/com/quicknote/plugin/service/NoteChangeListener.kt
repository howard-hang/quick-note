package com.quicknote.plugin.service

import com.intellij.util.messages.Topic
import com.quicknote.plugin.model.Note

/**
 * Listener interface for note change events
 * Uses IntelliJ's Message Bus for decoupled communication
 */
interface NoteChangeListener {
    companion object {
        val TOPIC = Topic.create(
            "NoteChangeListener",
            NoteChangeListener::class.java
        )
    }

    /**
     * Called when a new note is created
     */
    fun onNoteCreated(note: Note) {}

    /**
     * Called when a note is updated
     */
    fun onNoteUpdated(note: Note) {}

    /**
     * Called when a note is deleted
     */
    fun onNoteDeleted(noteId: String) {}
}
