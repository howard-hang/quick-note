package com.quicknote.plugin.service

import com.intellij.util.messages.Topic
import com.quicknote.plugin.model.Note

/**
 * Listener interface for note selection events in UI
 * Used to communicate between list and detail panels
 */
interface NoteSelectionListener {
    companion object {
        val TOPIC = Topic.create(
            "NoteSelectionListener",
            NoteSelectionListener::class.java
        )
    }

    /**
     * Called when a note is selected in the list
     */
    fun onNoteSelected(note: Note)
}
