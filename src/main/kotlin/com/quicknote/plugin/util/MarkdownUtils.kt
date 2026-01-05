package com.quicknote.plugin.util

import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Utility object for Markdown processing
 */
object MarkdownUtils {
    private val parser: Parser by lazy {
        Parser.builder().build()
    }

    private val renderer: HtmlRenderer by lazy {
        HtmlRenderer.builder().build()
    }

    /**
     * Convert Markdown text to HTML
     *
     * @param markdown Markdown text
     * @return HTML string
     */
    fun toHtml(markdown: String): String {
        val document: Node = parser.parse(markdown)
        return renderer.render(document)
    }

    /**
     * Create styled HTML with IDE-compatible CSS
     *
     * @param markdown Markdown text
     * @param isDarkTheme Whether to use dark theme styles
     * @return Complete HTML document with styles
     */
    fun toStyledHtml(markdown: String, isDarkTheme: Boolean = false): String {
        val html = toHtml(markdown)
        return """
            <html>
            <body>
                $html
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Format code snippet with language and line numbers
     *
     * @param code Code text
     * @param language Programming language
     * @param startLine Start line number (optional)
     * @param endLine End line number (optional)
     * @return Formatted Markdown code block
     */
    fun formatCodeSnippet(
        code: String,
        language: String,
        startLine: Int? = null,
        endLine: Int? = null
    ): String {
        val codeBlock = StringBuilder()
        codeBlock.appendLine("```$language")
        codeBlock.appendLine(code.trim())
        codeBlock.appendLine("```")

        if (startLine != null && endLine != null) {
            codeBlock.appendLine()
            codeBlock.appendLine("**Location**: Lines $startLine-$endLine")
        }

        return codeBlock.toString()
    }

    /**
     * Extract plain text from Markdown (strip formatting)
     *
     * @param markdown Markdown text
     * @return Plain text
     */
    fun toPlainText(markdown: String): String {
        // Simple approach: remove common Markdown syntax
        return markdown
            .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), "") // Remove code blocks
            .replace(Regex("`[^`]+`"), "") // Remove inline code
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "") // Remove headers
            .replace(Regex("[*_]{1,2}([^*_]+)[*_]{1,2}"), "$1") // Remove bold/italic
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1") // Replace links with text
            .trim()
    }
}
