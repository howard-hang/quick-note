package com.quicknote.plugin.repository

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.quicknote.plugin.model.Note
import com.quicknote.plugin.model.NoteConstants
import com.quicknote.plugin.model.NoteMetadata
import com.quicknote.plugin.model.NoteType
import com.quicknote.plugin.settings.QuickNoteSettings
import org.yaml.snakeyaml.Yaml
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.streams.toList

/**
 * Repository for note persistence
 * Handles file I/O operations for notes stored as Markdown + YAML Front Matter
 */
@Service(Service.Level.APP)
class NoteRepository {
    private val yaml = Yaml()

    companion object {
        fun getInstance(): NoteRepository {
            return checkNotNull(
                ApplicationManager.getApplication().getService(NoteRepository::class.java)
            ) { "NoteRepository not available" }
        }

        private const val FRONT_MATTER_DELIMITER = "---"
    }

    /**
     * Save note to file system
     *
     * @param projectName Project name (used for directory organization)
     * @param note Note to save
     * @return Path to the saved file
     */
    fun save(projectName: String, note: Note): Path {
        val noteDir = getNoteDirectory(projectName)
        Files.createDirectories(noteDir)

        val filePath = noteDir.resolve("${note.id}${NoteConstants.NOTE_FILE_EXTENSION}")
        val content = serializeNote(note)

        Files.writeString(filePath, content, StandardCharsets.UTF_8)
        thisLogger().debug("Saved note: ${note.id} to $filePath")

        return filePath
    }

    /**
     * Update existing note
     *
     * @param projectName Project name
     * @param note Note to update
     * @return Path to the updated file
     */
    fun update(projectName: String, note: Note): Path {
        return save(projectName, note)
    }

    /**
     * Delete note from file system
     *
     * @param projectName Project name
     * @param noteId Note ID
     * @return true if deleted successfully, false otherwise
     */
    fun delete(projectName: String, noteId: String): Boolean {
        val filePath = getNoteDirectory(projectName)
            .resolve("$noteId${NoteConstants.NOTE_FILE_EXTENSION}")

        return if (filePath.exists()) {
            Files.deleteIfExists(filePath)
            thisLogger().debug("Deleted note: $noteId")
            true
        } else {
            thisLogger().warn("Note file not found: $filePath")
            false
        }
    }

    /**
     * Find note by ID
     *
     * @param projectName Project name
     * @param noteId Note ID
     * @return Note if found, null otherwise
     */
    fun findById(projectName: String, noteId: String): Note? {
        val filePath = getNoteDirectory(projectName)
            .resolve("$noteId${NoteConstants.NOTE_FILE_EXTENSION}")

        return if (filePath.exists()) {
            deserializeNote(filePath)
        } else {
            null
        }
    }

    /**
     * Find all notes for a project
     *
     * @param projectName Project name
     * @return List of notes, sorted by modification time (newest first)
     */
    fun findAll(projectName: String): List<Note> {
        val noteDir = getNoteDirectory(projectName)

        if (!noteDir.exists() || !noteDir.isDirectory()) {
            thisLogger().debug("Note directory not found or not a directory: $noteDir")
            return emptyList()
        }

        return Files.walk(noteDir, 1)
            .filter { it.toString().endsWith(NoteConstants.NOTE_FILE_EXTENSION) }
            .use { stream ->
                stream.map { deserializeNote(it) }
                    .filter { it != null }
                    .map { it!! }
                    .toList()
                    .sortedByDescending { it.modifiedAt }
            }
    }

    /**
     * Find notes by file path
     *
     * @param projectName Project name
     * @param filePath File path to search for
     * @return List of notes associated with the file path
     */
    fun findByFilePath(projectName: String, filePath: String): List<Note> {
        return findAll(projectName).filter { it.filePath == filePath }
    }

    /**
     * Find notes by tag
     *
     * @param projectName Project name
     * @param tag Tag to search for
     * @return List of notes containing the tag
     */
    fun findByTag(projectName: String, tag: String): List<Note> {
        return findAll(projectName).filter { tag in it.tags }
    }

    /**
     * Count total notes for a project
     *
     * @param projectName Project name
     * @return Number of notes
     */
    fun count(projectName: String): Int {
        val noteDir = getNoteDirectory(projectName)

        if (!noteDir.exists() || !noteDir.isDirectory()) {
            return 0
        }

        return Files.walk(noteDir, 1)
            .filter { it.toString().endsWith(NoteConstants.NOTE_FILE_EXTENSION) }
            .count()
            .toInt()
    }

    /**
     * Serialize note to Markdown + YAML Front Matter format
     */
    private fun serializeNote(note: Note): String {
        val frontMatter = buildMap<String, Any> {
            put(NoteConstants.YAML_FIELD_ID, note.id)
            put(NoteConstants.YAML_FIELD_TITLE, note.title)
            put(NoteConstants.YAML_FIELD_TAGS, note.tags)
            put(NoteConstants.YAML_FIELD_FILE_PATH, note.filePath)
            put(NoteConstants.YAML_FIELD_PROJECT_NAME, note.projectName)
            put(NoteConstants.YAML_FIELD_TYPE, note.type.name)
            put(NoteConstants.YAML_FIELD_CREATED_AT, note.createdAt)
            put(NoteConstants.YAML_FIELD_MODIFIED_AT, note.modifiedAt)
            note.gitBranch?.takeIf { it.isNotBlank() }?.let {
                put(NoteConstants.YAML_FIELD_GIT_BRANCH, it)
            }

            // Add metadata if not default
            if (note.metadata != NoteMetadata()) {
                val metadataMap = buildMap<String, Any> {
                    note.metadata.language?.let { put(NoteConstants.META_LANGUAGE, it) }
                    note.metadata.startLine?.let { put(NoteConstants.META_START_LINE, it) }
                    note.metadata.endLine?.let { put(NoteConstants.META_END_LINE, it) }
                    note.metadata.snippet?.let { put(NoteConstants.META_SNIPPET, it) }
                    put(NoteConstants.META_IS_PINNED, note.metadata.isPinned)
                    note.metadata.color?.let { put(NoteConstants.META_COLOR, it) }
                }
                put(NoteConstants.YAML_FIELD_METADATA, metadataMap)
            }
        }

        val yamlString = yaml.dump(frontMatter)

        return buildString {
            appendLine(FRONT_MATTER_DELIMITER)
            append(yamlString)
            appendLine(FRONT_MATTER_DELIMITER)
            appendLine()
            append(note.content)
        }
    }

    /**
     * Deserialize note from Markdown + YAML Front Matter format
     */
    @Suppress("UNCHECKED_CAST")
    private fun deserializeNote(filePath: Path): Note? {
        return try {
            val content = Files.readString(filePath, StandardCharsets.UTF_8)

            // Split front matter and content
            val parts = content.split(FRONT_MATTER_DELIMITER)
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (parts.size < 2) {
                thisLogger().warn("Invalid note format (missing front matter): $filePath")
                return null
            }

            val frontMatterYaml = parts[0]
            val markdownContent = parts.subList(1, parts.size).joinToString("\n$FRONT_MATTER_DELIMITER\n")

            // Parse YAML
            val frontMatter = yaml.load<Map<String, Any>>(frontMatterYaml)
            if (frontMatter.isNullOrEmpty()) {
                thisLogger().warn("Empty front matter in note: $filePath")
            }

            // Parse metadata
            val metadataMap = frontMatter[NoteConstants.YAML_FIELD_METADATA] as? Map<String, Any>
            val metadata = if (metadataMap != null) {
                NoteMetadata(
                    language = metadataMap[NoteConstants.META_LANGUAGE] as? String,
                    startLine = metadataMap[NoteConstants.META_START_LINE] as? Int,
                    endLine = metadataMap[NoteConstants.META_END_LINE] as? Int,
                    snippet = metadataMap[NoteConstants.META_SNIPPET] as? String,
                    isPinned = metadataMap[NoteConstants.META_IS_PINNED] as? Boolean ?: false,
                    color = metadataMap[NoteConstants.META_COLOR] as? String
                )
            } else {
                NoteMetadata()
            }
            val gitBranch = (frontMatter[NoteConstants.YAML_FIELD_GIT_BRANCH] as? String)
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            Note(
                id = frontMatter[NoteConstants.YAML_FIELD_ID] as String,
                title = frontMatter[NoteConstants.YAML_FIELD_TITLE] as String,
                content = markdownContent,
                tags = (frontMatter[NoteConstants.YAML_FIELD_TAGS] as? List<String>) ?: emptyList(),
                filePath = frontMatter[NoteConstants.YAML_FIELD_FILE_PATH] as String,
                projectName = frontMatter[NoteConstants.YAML_FIELD_PROJECT_NAME] as String,
                type = NoteType.valueOf(frontMatter[NoteConstants.YAML_FIELD_TYPE] as String),
                createdAt = (frontMatter[NoteConstants.YAML_FIELD_CREATED_AT] as Number).toLong(),
                modifiedAt = (frontMatter[NoteConstants.YAML_FIELD_MODIFIED_AT] as Number).toLong(),
                gitBranch = gitBranch,
                metadata = metadata
            )
        } catch (e: Exception) {
            thisLogger().error("Failed to deserialize note: $filePath", e)
            null
        }
    }

    /**
     * Get note storage directory for a project
     */
    private fun getNoteDirectory(projectName: String): Path {
        val settings = QuickNoteSettings.getInstance()
        val baseDir = settings.storageBasePath
        return Paths.get(baseDir, projectName)
    }
}
