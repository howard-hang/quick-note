package com.quicknote.plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.quicknote.plugin.model.NoteConstants
import java.nio.file.Paths

/**
 * Application-level settings for Quick Note plugin
 * Persisted in QuickNoteSettings.xml
 */
@Service(Service.Level.APP)
@State(
    name = "QuickNoteSettings",
    storages = [Storage("QuickNoteSettings.xml")]
)
class QuickNoteSettings : PersistentStateComponent<QuickNoteSettings.State> {

    /**
     * Settings state data class
     */
    data class State(
        var storageBasePath: String = getDefaultStoragePath(),
        var useProjectBasedOrganization: Boolean = true,
        var createBackupOnSave: Boolean = false,
        var enableFullTextSearch: Boolean = true,
        var searchInTags: Boolean = true,
        var searchInFilePaths: Boolean = true,
        var resultsPerPage: Int = 100,
        var enableMarkdownPreview: Boolean = true,
        var showLineNumbers: Boolean = true,
        var syntaxHighlighting: Boolean = true,
        var defaultTags: String = "API,UI",
        var searchDebounceMs: Long = 300L
    )

    private var state = State()

    companion object {
        fun getInstance(): QuickNoteSettings {
            return checkNotNull(
                ApplicationManager.getApplication().getService(QuickNoteSettings::class.java)
            ) { "QuickNoteSettings not available" }
        }

        /**
         * Get default storage path in user's home directory
         */
        private fun getDefaultStoragePath(): String {
            val userHome = System.getProperty("user.home")
            return Paths.get(userHome, NoteConstants.DEFAULT_STORAGE_DIR_NAME).toString()
        }
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    // Convenient property accessors
    var storageBasePath: String
        get() = state.storageBasePath
        set(value) {
            state.storageBasePath = value
        }

    var useProjectBasedOrganization: Boolean
        get() = state.useProjectBasedOrganization
        set(value) {
            state.useProjectBasedOrganization = value
        }

    var createBackupOnSave: Boolean
        get() = state.createBackupOnSave
        set(value) {
            state.createBackupOnSave = value
        }

    var enableFullTextSearch: Boolean
        get() = state.enableFullTextSearch
        set(value) {
            state.enableFullTextSearch = value
        }

    var searchInTags: Boolean
        get() = state.searchInTags
        set(value) {
            state.searchInTags = value
        }

    var searchInFilePaths: Boolean
        get() = state.searchInFilePaths
        set(value) {
            state.searchInFilePaths = value
        }

    var resultsPerPage: Int
        get() = state.resultsPerPage
        set(value) {
            state.resultsPerPage = value
        }

    var enableMarkdownPreview: Boolean
        get() = state.enableMarkdownPreview
        set(value) {
            state.enableMarkdownPreview = value
        }

    var showLineNumbers: Boolean
        get() = state.showLineNumbers
        set(value) {
            state.showLineNumbers = value
        }

    var syntaxHighlighting: Boolean
        get() = state.syntaxHighlighting
        set(value) {
            state.syntaxHighlighting = value
        }

    var defaultTags: String
        get() = state.defaultTags
        set(value) {
            state.defaultTags = value
        }

    var searchDebounceMs: Long
        get() = state.searchDebounceMs
        set(value) {
            state.searchDebounceMs = value
        }

    /**
     * Get default tags as list
     */
    fun getDefaultTagsList(): List<String> {
        if (defaultTags.isBlank()) {
            return emptyList()
        }
        val unique = LinkedHashSet<String>()
        defaultTags.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { unique.add(it) }
        return unique.toList()
    }

    fun getStoredTags(): List<String> {
        return getDefaultTagsList()
    }

    fun setStoredTags(tags: List<String>, maxSize: Int = 20) {
        val unique = LinkedHashSet<String>()
        tags.map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { unique.add(it) }
        defaultTags = unique.take(maxSize).joinToString(",")
    }

    fun getCommonTags(limit: Int = 4): List<String> {
        return getDefaultTagsList().take(limit)
    }

    fun recordCommonTags(tags: List<String>, maxSize: Int = 20) {
        val normalized = tags.map { it.trim() }.filter { it.isNotEmpty() }
        if (normalized.isEmpty()) {
            return
        }
        val newTags = LinkedHashSet<String>()
        normalized.forEach { newTags.add(it) }
        val existing = getDefaultTagsList()
        val updated = mutableListOf<String>()
        newTags.forEach { updated.add(it) }
        existing.filterNot { it in newTags }.forEach { updated.add(it) }
        setStoredTags(updated, maxSize)
    }

    /**
     * Reset to default values
     */
    fun resetToDefaults() {
        state = State()
    }
}
