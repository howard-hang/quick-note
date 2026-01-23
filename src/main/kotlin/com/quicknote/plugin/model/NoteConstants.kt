package com.quicknote.plugin.model

/**
 * Constants used throughout the plugin
 */
object NoteConstants {
    // File and directory constants
    const val DEFAULT_STORAGE_DIR_NAME = "QuickNotes"
    const val INDEX_DIR_NAME = ".index"
    const val NOTE_FILE_EXTENSION = ".md"

    // YAML Front Matter field names
    const val YAML_FIELD_ID = "id"
    const val YAML_FIELD_TITLE = "title"
    const val YAML_FIELD_TAGS = "tags"
    const val YAML_FIELD_FILE_PATH = "filePath"
    const val YAML_FIELD_PROJECT_NAME = "projectName"
    const val YAML_FIELD_TYPE = "type"
    const val YAML_FIELD_CREATED_AT = "createdAt"
    const val YAML_FIELD_MODIFIED_AT = "modifiedAt"
    const val YAML_FIELD_GIT_BRANCH = "gitBranch"
    const val YAML_FIELD_METADATA = "metadata"

    // Metadata field names
    const val META_LANGUAGE = "language"
    const val META_START_LINE = "startLine"
    const val META_END_LINE = "endLine"
    const val META_SNIPPET = "snippet"
    const val META_IS_PINNED = "isPinned"
    const val META_COLOR = "color"

    // Lucene index field names
    const val LUCENE_FIELD_ID = "id"
    const val LUCENE_FIELD_TITLE = "title"
    const val LUCENE_FIELD_CONTENT = "content"
    const val LUCENE_FIELD_TAGS = "tags"
    const val LUCENE_FIELD_FILE_PATH = "filePath"
    const val LUCENE_FIELD_FILE_PATH_EXACT = "filePathExact"
    const val LUCENE_FIELD_GIT_BRANCH = "gitBranch"

    // UI constants
    const val TOOL_WINDOW_ID = "Quick Notes"
    const val MAX_TAGS_DISPLAY = 3
    const val MAX_SEARCH_RESULTS = 100

    // Search constants
    const val SEARCH_DEBOUNCE_MS = 300L

    const val DEFAULT_GIT_BRANCH = "no-branch"

    // Time format
    const val TIME_FORMAT_FULL = "MMM dd, yyyy HH:mm"
    const val TIME_FORMAT_SHORT = "MMM dd, yyyy"
}
