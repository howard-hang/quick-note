package com.quicknote.plugin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.quicknote.plugin.model.Note
import com.quicknote.plugin.model.NoteConstants
import com.quicknote.plugin.repository.NoteRepository
import java.util.UUID

/**
 * Project-level service for note operations
 * Provides CRUD operations and coordinates with repository and search service
 */
@Service(Service.Level.PROJECT)
class NoteService(private val project: Project) {

    private val repository = NoteRepository.getInstance()

    companion object {
        fun getInstance(project: Project): NoteService {
            return checkNotNull(project.getService(NoteService::class.java)) {
                "NoteService not available"
            }
        }
    }

    /**
     * Save a new note
     *
     * @param note Note to save (ID will be generated if empty)
     * @return Result with the saved note or error
     */
    fun saveNote(note: Note): Result<Note> {
        return try {
            val resolvedBranch = resolveGitBranch(note.gitBranch)
            // Generate UUID if not provided
            val noteWithId = if (note.id.isBlank()) {
                note.copy(
                    id = UUID.randomUUID().toString(),
                    projectName = project.name,
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis(),
                    gitBranch = resolvedBranch
                )
            } else {
                note.copy(
                    projectName = project.name,
                    modifiedAt = System.currentTimeMillis(),
                    gitBranch = resolvedBranch
                )
            }

            // Save to file system
            repository.save(project.name, noteWithId)

            // Publish event
            project.messageBus
                .syncPublisher(NoteChangeListener.TOPIC)
                .onNoteCreated(noteWithId)

            thisLogger().info("Note saved: ${noteWithId.id}")
            Result.success(noteWithId)
        } catch (e: Exception) {
            thisLogger().error("Failed to save note", e)
            Result.failure(e)
        }
    }

    /**
     * Update an existing note
     *
     * @param note Note to update
     * @return Result with the updated note or error
     */
    fun updateNote(note: Note): Result<Note> {
        return try {
            val resolvedBranch = resolveGitBranch(note.gitBranch)
            val updatedNote = note.copy(
                projectName = project.name,
                modifiedAt = System.currentTimeMillis(),
                gitBranch = resolvedBranch
            )

            repository.update(project.name, updatedNote)

            // Publish event
            project.messageBus
                .syncPublisher(NoteChangeListener.TOPIC)
                .onNoteUpdated(updatedNote)

            thisLogger().info("Note updated: ${updatedNote.id}")
            Result.success(updatedNote)
        } catch (e: Exception) {
            thisLogger().error("Failed to update note: ${note.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a note
     *
     * @param noteId ID of the note to delete
     * @return Result with success or error
     */
    fun deleteNote(noteId: String): Result<Unit> {
        return try {
            val deleted = repository.delete(project.name, noteId)

            if (deleted) {
                // Publish event
                project.messageBus
                    .syncPublisher(NoteChangeListener.TOPIC)
                    .onNoteDeleted(noteId)

                thisLogger().info("Note deleted: $noteId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Note not found: $noteId"))
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to delete note: $noteId", e)
            Result.failure(e)
        }
    }

    /**
     * Get a single note by ID
     *
     * @param noteId Note ID
     * @return Note if found, null otherwise
     */
    fun getNote(noteId: String): Note? {
        return repository.findById(project.name, noteId)
    }

    /**
     * Get all notes for the current project
     *
     * @return List of all notes, sorted by modification time (newest first)
     */
    fun getAllNotes(): List<Note> {
        return repository.findAll(project.name)
    }

    /**
     * Find notes by file path
     *
     * @param filePath Relative file path
     * @return List of notes associated with the file
     */
    fun findNotesByFilePath(filePath: String): List<Note> {
        return repository.findByFilePath(project.name, filePath)
    }

    /**
     * Find notes by tag
     *
     * @param tag Tag to search for
     * @return List of notes containing the tag
     */
    fun findNotesByTag(tag: String): List<Note> {
        return repository.findByTag(project.name, tag)
    }

    /**
     * Get note count for current project
     *
     * @return Number of notes
     */
    fun getNoteCount(): Int {
        return repository.count(project.name)
    }

    /**
     * Get all unique tags from all notes
     *
     * @return Set of all tags
     */
    fun getAllTags(): Set<String> {
        return getAllNotes()
            .flatMap { it.tags }
            .toSet()
    }

    /**
     * Get pinned notes
     *
     * @return List of pinned notes
     */
    fun getPinnedNotes(): List<Note> {
        return getAllNotes().filter { it.isPinned() }
    }

    private fun resolveGitBranch(rawBranch: String?): String {
        val normalized = rawBranch?.trim().orEmpty()
        if (normalized.isNotBlank()) {
            return normalized
        }
        val current = GitBranchService.getInstance(project).getCurrentBranch()
        return current ?: NoteConstants.DEFAULT_GIT_BRANCH
    }
}
