package com.quicknote.plugin.model

/**
 * Additional metadata for notes
 *
 * @property language Programming language (for code snippets only)
 * @property startLine Start line number in source file (for code snippets only)
 * @property endLine End line number in source file (for code snippets only)
 * @property snippet Original code snippet text (for code snippets only)
 * @property isPinned Whether the note is pinned to the top
 * @property color Optional color marker for the note
 */
data class NoteMetadata(
    val language: String? = null,
    val startLine: Int? = null,
    val endLine: Int? = null,
    val snippet: String? = null,
    val isPinned: Boolean = false,
    val color: String? = null
)
