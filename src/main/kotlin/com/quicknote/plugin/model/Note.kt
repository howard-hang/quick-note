package com.quicknote.plugin.model

/**
 * Note data model
 *
 * @property id Unique identifier (UUID)
 * @property title Note title
 * @property content Markdown content
 * @property tags List of tags
 * @property filePath Relative path to the associated source file
 * @property projectName Name of the project this note belongs to
 * @property type Type of the note (NOTE or SNIPPET)
 * @property createdAt Creation timestamp in milliseconds
 * @property modifiedAt Last modification timestamp in milliseconds
 * @property metadata Additional metadata
 */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val tags: List<String>,
    val filePath: String,
    val projectName: String,
    val type: NoteType,
    val createdAt: Long,
    val modifiedAt: Long,
    val metadata: NoteMetadata = NoteMetadata()
) {
    /**
     * Returns a display-friendly file path
     */
    fun getDisplayPath(): String {
        return if (filePath.length > 50) {
            "...${filePath.takeLast(47)}"
        } else {
            filePath
        }
    }

    /**
     * Returns whether this note has any tags
     */
    fun hasTags(): Boolean = tags.isNotEmpty()

    /**
     * Returns whether this is a code snippet
     */
    fun isSnippet(): Boolean = type == NoteType.SNIPPET

    /**
     * Returns whether this note is pinned
     */
    fun isPinned(): Boolean = metadata.isPinned
}
