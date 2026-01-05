package com.quicknote.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import com.quicknote.plugin.model.Note
import com.quicknote.plugin.model.NoteType
import com.quicknote.plugin.settings.QuickNoteSettings
import com.quicknote.plugin.service.NoteChangeListener
import com.quicknote.plugin.service.NoteService
import com.quicknote.plugin.service.SearchService
import com.quicknote.plugin.ui.dialog.EditNoteDialog
import com.quicknote.plugin.util.MarkdownUtils
import com.quicknote.plugin.util.PathUtils
import com.quicknote.plugin.util.TimeFormatter
import java.awt.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.concurrent.timer
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

/**
 * Main content for Quick Notes tool window
 */
class QuickNoteToolWindowContent(private val project: Project) : Disposable {

    companion object {
        val CONTENT_KEY: Key<QuickNoteToolWindowContent> = Key.create("QuickNoteToolWindowContent")
    }

    private val searchField = JBTextField()
    private val resetSearchButton = JButton(IconLoader.getIcon("/icons/clear.svg", QuickNoteToolWindowContent::class.java))
    private val noteListModel = DefaultListModel<Note>()
    private val noteList = JBList(noteListModel)
    private val detailPanel = JEditorPane()
    private val snippetPanel = JEditorPane()
    private val emptyLabel = JBLabel("Select a note to view details", SwingConstants.CENTER)
    private val snippetDetailProportion = 0.35f
    private val splitPlaceholder = JBPanel<JBPanel<*>>()

    // Detail panel components
    private lateinit var detailScrollPane: JScrollPane
    private lateinit var snippetScrollPane: JScrollPane
    private lateinit var detailSplitPane: Splitter
    private lateinit var detailContentPanel: JPanel

    private var currentNote: Note? = null
    private var filePathFilter: String? = null
    private var searchTimer: java.util.Timer? = null
    private val searchSequence = AtomicInteger(0)

    val component: JComponent by lazy {
        createUI()
    }

    init {
        setupListeners()
        loadNotesForCurrentFilter()
        rebuildSearchIndexIfNeeded()
        Disposer.register(project, this)
    }

    private fun rebuildSearchIndexIfNeeded() {
        if (!QuickNoteSettings.getInstance().enableFullTextSearch) {
            return
        }

        // Rebuild search index in background
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val searchService = SearchService.getInstance(project)
                val noteService = NoteService.getInstance(project)
                val allNotes = noteService.getAllNotes()

                thisLogger().info("Rebuilding search index for ${allNotes.size} notes")

                allNotes.forEach { note ->
                    try {
                        searchService.indexNote(note)
                    } catch (e: Exception) {
                        thisLogger().warn("Failed to index note: ${note.id}", e)
                    }
                }

                thisLogger().info("Search index rebuilt successfully")
            } catch (e: Throwable) {
                thisLogger().error("Failed to rebuild search index", e)
            }
        }
    }

    private fun createUI(): JComponent {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())

        // Top search panel
        mainPanel.add(createSearchPanel(), BorderLayout.NORTH)

        // Center split pane with list and detail
        val detailPanel = createDetailPanel()
        val listPanel = createListPanel()
        val splitter = Splitter(false, 0.4f).apply {
            setFirstComponent(listPanel)
            setSecondComponent(detailPanel)
        }
        mainPanel.add(splitter, BorderLayout.CENTER)

        return mainPanel
    }

    private fun createSearchPanel(): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.border = JBUI.Borders.empty(8)

        updateSearchPlaceholder()
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = scheduleSearch()
            override fun removeUpdate(e: DocumentEvent?) = scheduleSearch()
            override fun changedUpdate(e: DocumentEvent?) = scheduleSearch()
        })

        panel.add(searchField, BorderLayout.CENTER)
        panel.add(createSearchActionsPanel(), BorderLayout.EAST)
        return panel
    }

    private fun createSearchActionsPanel(): JComponent {
        resetSearchButton.apply {
            toolTipText = "Clear search"
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            isOpaque = false
            addActionListener {
                if (searchField.text.isNotBlank()) {
                    searchField.text = ""
                    performSearch()
                }
            }
        }

        val settingsButton = JButton(AllIcons.General.Settings).apply {
            toolTipText = "Quick Note Settings"
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            isOpaque = false
            addActionListener { showStorageSettingsDialog() }
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.emptyLeft(6)
            add(resetSearchButton, BorderLayout.WEST)
            add(settingsButton, BorderLayout.EAST)
        }
    }

    private fun showStorageSettingsDialog() {
        val settings = QuickNoteSettings.getInstance()
        val dialog = StoragePathDialog(project, settings.storageBasePath)
        if (dialog.showAndGet()) {
            val newPath = dialog.storagePath
            if (newPath != settings.storageBasePath) {
                settings.storageBasePath = newPath
                loadNotesForCurrentFilter()
                if (settings.enableFullTextSearch) {
                    SearchService.getInstance(project).resetIndex()
                }
            }
        }
    }

    private fun createListPanel(): JComponent {
        noteList.cellRenderer = NoteCellRenderer()
        noteList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        noteList.fixedCellHeight = JBUI.scale(50)

        noteList.addListSelectionListener { e ->
            thisLogger().debug("List selection event - valueIsAdjusting: ${e.valueIsAdjusting}")
            val selectedNote = noteList.selectedValue
            thisLogger().debug("Selected note: ${selectedNote?.title ?: "null"}")
            if (selectedNote != null) {
                displayNoteDetails(selectedNote)
            } else {
                showEmptyState("Select a note to view details")
            }
        }

        return JBScrollPane(noteList)
    }

    private fun createDetailPanel(): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())

        // Detail view
        detailPanel.contentType = "text/html"
        detailPanel.isEditable = false
        detailPanel.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        detailScrollPane = JBScrollPane(detailPanel)

        // Snippet view
        snippetPanel.contentType = "text/html"
        snippetPanel.isEditable = false
        snippetPanel.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        snippetScrollPane = JBScrollPane(snippetPanel)

        detailSplitPane = Splitter(true, snippetDetailProportion).apply {
            setFirstComponent(detailScrollPane)
            setSecondComponent(snippetScrollPane)
        }

        // Action buttons
        val buttonPanel = JBPanel<JBPanel<*>>().apply {
            layout = FlowLayout(FlowLayout.RIGHT)

            val openFileButton = JButton("Open File")
            openFileButton.addActionListener { currentNote?.let { openNoteFile(it) } }
            add(openFileButton)

            val editButton = JButton("Edit")
            editButton.addActionListener { currentNote?.let { editNote(it) } }
            add(editButton)

            val deleteButton = JButton("Delete")
            deleteButton.addActionListener { currentNote?.let { deleteNote(it) } }
            add(deleteButton)
        }

        // Container for content (can switch between scrollPane and emptyLabel)
        detailContentPanel = JBPanel<JBPanel<*>>(BorderLayout())

        // Initially show empty state
        detailContentPanel.add(emptyLabel, BorderLayout.CENTER)

        panel.add(detailContentPanel, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        thisLogger().debug("Detail panel created - scrollPane: $detailScrollPane, contentPanel: $detailContentPanel")

        return panel
    }

    private fun setupListeners() {
        // Listen to note changes
        project.messageBus.connect(this).subscribe(
            NoteChangeListener.TOPIC,
            object : NoteChangeListener {
                override fun onNoteCreated(note: Note) {
                    loadNotesForCurrentFilter()
                }

                override fun onNoteUpdated(note: Note) {
                    loadNotesForCurrentFilter()
                    if (currentNote?.id == note.id) {
                        SwingUtilities.invokeLater { displayNoteDetails(note) }
                    }
                }

                override fun onNoteDeleted(noteId: String) {
                    loadNotesForCurrentFilter()
                    if (currentNote?.id == noteId) {
                        currentNote = null
                        SwingUtilities.invokeLater {
                            showEmptyState("Note deleted")
                        }
                    }
                }
            }
        )
    }

    private fun scheduleSearch() {
        searchTimer?.cancel()
        val debounceMs = QuickNoteSettings.getInstance().searchDebounceMs
        searchTimer = timer(initialDelay = debounceMs, period = Long.MAX_VALUE) {
            SwingUtilities.invokeLater {
                performSearch()
            }
        }
    }

    private fun performSearch() {
        val query = searchField.text.trim()
        val sequence = searchSequence.incrementAndGet()
        val currentFileFilter = filePathFilter

        thisLogger().debug("Performing search with query: '$query'")

        ApplicationManager.getApplication().executeOnPooledThread {
            if (query.isBlank()) {
                val notes = if (currentFileFilter.isNullOrBlank()) {
                    NoteService.getInstance(project).getAllNotes()
                } else {
                    NoteService.getInstance(project).findNotesByFilePath(currentFileFilter)
                }
                thisLogger().debug("Query is blank, showing ${notes.size} notes")
                applySearchResults(sequence, query, notes, null)
                return@executeOnPooledThread
            }

            val searchService = SearchService.getInstance(project)
            if (!searchService.isSearchAvailable()) {
                applySearchResults(sequence, query, emptyList(), "Search unavailable")
                return@executeOnPooledThread
            }

            val maxResults = maxOf(1, QuickNoteSettings.getInstance().resultsPerPage)
            val notes = try {
                val searchResults = searchService.search(query, maxResults)
                thisLogger().debug("Search returned ${searchResults.size} results for query: '$query'")
                searchResults
            } catch (e: Exception) {
                thisLogger().error("Search failed for query: '$query'", e)
                emptyList()
            }
            val filteredNotes = if (currentFileFilter.isNullOrBlank()) {
                notes
            } else {
                notes.filter { it.filePath == currentFileFilter }
            }
            applySearchResults(sequence, query, filteredNotes, "No notes found")
        }
    }

    private fun loadNotesForCurrentFilter() {
        val notes = if (filePathFilter.isNullOrBlank()) {
            NoteService.getInstance(project).getAllNotes()
        } else {
            NoteService.getInstance(project).findNotesByFilePath(filePathFilter!!)
        }
        updateNoteList(notes)
    }

    fun showNotesForFile(filePath: String) {
        filePathFilter = filePath
        updateSearchPlaceholder()
        searchField.text = ""
        loadNotesForCurrentFilter()
    }

    private fun updateSearchPlaceholder() {
        val filterName = filePathFilter?.let { PathUtils.getFileName(it) }
        searchField.emptyText.text = if (filterName.isNullOrBlank()) {
            "Search notes..."
        } else {
            "Search notes in $filterName..."
        }
    }

    private fun updateNoteList(notes: List<Note>) {
        if (SwingUtilities.isEventDispatchThread()) {
            updateNoteListInternal(notes)
        } else {
            SwingUtilities.invokeLater { updateNoteListInternal(notes) }
        }
    }

    private fun updateNoteListInternal(notes: List<Note>) {
        thisLogger().debug("Updating note list with ${notes.size} notes")

        noteListModel.clear()
        notes.forEach { noteListModel.addElement(it) }

        if (notes.isEmpty()) {
            thisLogger().debug("Note list is empty")

            // Show empty state in content panel
            showEmptyState("No notes found")
            currentNote = null
        } else if (currentNote != null && notes.none { it.id == currentNote?.id }) {
            showEmptyState("Select a note to view details")
            currentNote = null
        }
    }

    private fun applySearchResults(
        sequence: Int,
        query: String,
        notes: List<Note>,
        emptyMessage: String?
    ) {
        SwingUtilities.invokeLater {
            if (sequence != searchSequence.get() || searchField.text.trim() != query) {
                return@invokeLater
            }
            if (notes.isEmpty() && emptyMessage != null) {
                noteListModel.clear()
                showEmptyState(emptyMessage)
                currentNote = null
                return@invokeLater
            }
            updateNoteListInternal(notes)
        }
    }

    private fun displayNoteDetails(note: Note) {
        currentNote = note
        thisLogger().info("displayNoteDetails called for note: ${note.title}")

        val snippetDisplay = buildSnippetDisplay(note)
        val detailsMarkdown = snippetDisplay.details
        val snippetMarkdown = snippetDisplay.snippetMarkdown

        val detailHtml = MarkdownUtils.toStyledHtml(detailsMarkdown, isDarkTheme = false)
        val detailFallbackHtml = wrapHtml(MarkdownUtils.toHtml(detailsMarkdown))
        val detailPlainText = MarkdownUtils.toPlainText(detailsMarkdown)

        val snippetHtml = snippetMarkdown?.let { MarkdownUtils.toStyledHtml(it, isDarkTheme = false) }
        val snippetFallbackHtml = snippetMarkdown?.let { wrapHtml(MarkdownUtils.toHtml(it)) }
        val snippetPlainText = snippetMarkdown?.let { MarkdownUtils.toPlainText(it) }

        thisLogger().debug("Generated HTML length: ${detailHtml.length}")

        SwingUtilities.invokeLater {
            thisLogger().debug("Using member variables - contentPanel: $detailContentPanel, scrollPane: $detailScrollPane")

            // Remove all components and add detail content
            detailContentPanel.removeAll()
            if (note.type == NoteType.SNIPPET && !snippetMarkdown.isNullOrBlank() && snippetHtml != null) {
                detailSplitPane.proportion = snippetDetailProportion
                detailSplitPane.setFirstComponent(detailScrollPane)
                detailSplitPane.setSecondComponent(snippetScrollPane)
                detailContentPanel.add(detailSplitPane, BorderLayout.CENTER)
            } else {
                detailSplitPane.setFirstComponent(splitPlaceholder)
                detailContentPanel.add(detailScrollPane, BorderLayout.CENTER)
            }

            // Set HTML content
            applyHtmlWithFallback(detailPanel, detailHtml, detailFallbackHtml, detailPlainText)
            detailPanel.caretPosition = 0  // Scroll to top
            if (snippetHtml != null && snippetFallbackHtml != null && snippetPlainText != null) {
                applyHtmlWithFallback(snippetPanel, snippetHtml, snippetFallbackHtml, snippetPlainText)
                snippetPanel.caretPosition = 0
            }

            // Ensure components are visible
            detailScrollPane.isVisible = true
            detailPanel.isVisible = true
            detailContentPanel.isVisible = true
            snippetScrollPane.isVisible = true
            snippetPanel.isVisible = true

            thisLogger().debug("Components visibility set - scrollPane: ${detailScrollPane.isVisible}, detailPanel: ${detailPanel.isVisible}, contentPanel: ${detailContentPanel.isVisible}")

            // Refresh the panel hierarchy
            detailContentPanel.revalidate()
            detailContentPanel.repaint()

            // Also refresh parent panel
            detailContentPanel.parent?.revalidate()
            detailContentPanel.parent?.repaint()

            thisLogger().info("Note details displayed successfully")
        }
    }

    private fun openNoteFile(note: Note) {
        val absolutePath = PathUtils.getAbsolutePath(project, note.filePath)
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath)

        if (virtualFile != null && virtualFile.exists()) {
            val descriptor = if (note.metadata.startLine != null) {
                OpenFileDescriptor(project, virtualFile, note.metadata.startLine!! - 1, 0)
            } else {
                OpenFileDescriptor(project, virtualFile)
            }
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        } else {
            Messages.showErrorDialog(
                project,
                "File not found: ${note.filePath}",
                "Error"
            )
        }
    }

    private fun deleteNote(note: Note) {
        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete this note?\n\n\"${note.title}\"",
            "Confirm Delete",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            val deleteResult = NoteService.getInstance(project).deleteNote(note.id)
            deleteResult.onSuccess {
                SearchService.getInstance(project).removeFromIndex(note.id)
                currentNote = null

                // Show empty state
                SwingUtilities.invokeLater {
                    showEmptyState("Note deleted")
                }
            }.onFailure { error ->
                Messages.showErrorDialog(project, "Failed to delete note: ${error.message}", "Error")
            }
        }
    }

    private fun editNote(note: Note) {
        val dialog = EditNoteDialog(project, note)
        if (dialog.showAndGet()) {
            dialog.updatedNote?.let { updated ->
                if (updated.id == currentNote?.id) {
                    displayNoteDetails(updated)
                }
            }
        }
    }

    override fun dispose() {
        searchTimer?.cancel()
        thisLogger().debug("QuickNoteToolWindowContent disposed")
    }

    /**
     * Custom cell renderer for note list
     */
    private class NoteCellRenderer : ListCellRenderer<Note> {
        override fun getListCellRendererComponent(
            list: JList<out Note>,
            value: Note,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val panel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout(5, 2)
                border = JBUI.Borders.empty(2)
                isOpaque = true
            }

            // Title
            val titleLabel = JBLabel(value.title).apply {
                font = font.deriveFont(Font.BOLD)
            }
            panel.add(titleLabel, BorderLayout.NORTH)

            // Metadata (tags + file)
            val metaPanel = JBPanel<JBPanel<*>>().apply {
                layout = FlowLayout(FlowLayout.LEFT, 5, 0)
                isOpaque = true

                // Show up to 3 tags
                value.tags.take(3).forEach { tag ->
                    add(JBLabel("[$tag]").apply {
                        foreground = JBColor.GRAY
                        font = font.deriveFont(Font.PLAIN, 11f)
                    })
                }

                if (value.tags.size > 3) {
                    add(JBLabel("+${value.tags.size - 3} more").apply {
                        foreground = JBColor.GRAY
                        font = font.deriveFont(Font.PLAIN, 11f)
                    })
                }
            }

            // Footer (file path + time)
            val footerPanel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout()
                isOpaque = true

                add(JBLabel(PathUtils.getShortenedPath(value.filePath, 40)).apply {
                    foreground = JBColor.GRAY
                    font = font.deriveFont(Font.PLAIN, 11f)
                }, BorderLayout.WEST)

                add(JBLabel(TimeFormatter.formatRelativeTime(value.modifiedAt)).apply {
                    foreground = JBColor.GRAY
                    font = font.deriveFont(Font.PLAIN, 11f)
                }, BorderLayout.EAST)
            }

            val centerPanel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout()
                isOpaque = true
                add(metaPanel, BorderLayout.NORTH)
                add(footerPanel, BorderLayout.SOUTH)
            }

            panel.add(centerPanel, BorderLayout.CENTER)

            // Selection styling
            val background = if (isSelected) list.selectionBackground else list.background
            val foreground = if (isSelected) list.selectionForeground else list.foreground
            panel.background = background
            panel.foreground = foreground
            centerPanel.background = background
            metaPanel.background = background
            footerPanel.background = background
            titleLabel.foreground = foreground
            metaPanel.components.forEach { component ->
                if (component is JLabel) {
                    component.foreground = if (isSelected) foreground else JBColor.GRAY
                }
            }
            footerPanel.components.forEach { component ->
                if (component is JLabel) {
                    component.foreground = if (isSelected) foreground else JBColor.GRAY
                }
            }

            return panel
        }
    }

    private fun showEmptyState(message: String) {
        if (!::detailContentPanel.isInitialized) {
            return
        }
        detailContentPanel.removeAll()
        emptyLabel.text = message
        detailContentPanel.add(emptyLabel, BorderLayout.CENTER)
        detailContentPanel.revalidate()
        detailContentPanel.repaint()
    }

    private data class SnippetDisplay(val details: String, val snippetMarkdown: String?)

    private data class SnippetExtraction(
        val code: String,
        val language: String?,
        val details: String,
        val startLine: Int?,
        val endLine: Int?
    )

    private fun buildSnippetDisplay(note: Note): SnippetDisplay {
        if (note.type != NoteType.SNIPPET) {
            return SnippetDisplay(note.content, null)
        }

        val rawSnippet = note.metadata.snippet?.trim().orEmpty()
        if (rawSnippet.isNotBlank()) {
            val snippetMarkdown = MarkdownUtils.formatCodeSnippet(
                rawSnippet,
                note.metadata.language?.trim().orEmpty(),
                note.metadata.startLine,
                note.metadata.endLine
            ).trim()
            val details = stripLegacySnippetFromContent(note.content, snippetMarkdown)
            return SnippetDisplay(details, snippetMarkdown)
        }

        val extracted = extractSnippetFromContent(note.content)
        if (extracted != null) {
            val snippetMarkdown = MarkdownUtils.formatCodeSnippet(
                extracted.code,
                extracted.language?.trim().orEmpty(),
                extracted.startLine,
                extracted.endLine
            ).trim()
            val details = if (extracted.details.isBlank()) "No additional notes." else extracted.details
            return SnippetDisplay(details, snippetMarkdown)
        }

        val details = if (note.content.isBlank()) "No additional notes." else note.content
        return SnippetDisplay(details, null)
    }

    private fun stripLegacySnippetFromContent(
        content: String,
        snippetMarkdown: String
    ): String {
        val normalizedContent = normalizeLineEndings(content).trim()
        val normalizedSnippetMarkdown = normalizeLineEndings(snippetMarkdown).trim()
        if (normalizedContent.endsWith(normalizedSnippetMarkdown)) {
            val stripped = normalizedContent.removeSuffix(normalizedSnippetMarkdown).trim()
            return if (stripped.isBlank()) "No additional notes." else stripped
        }

        return if (normalizedContent.isBlank()) "No additional notes." else normalizedContent
    }

    private fun normalizeLineEndings(text: String): String {
        return text.replace("\r\n", "\n")
    }

    private fun extractSnippetFromContent(content: String): SnippetExtraction? {
        val normalized = normalizeLineEndings(content).trim()
        val closingFenceIndex = normalized.lastIndexOf("```")
        if (closingFenceIndex == -1) {
            return null
        }

        val openingFenceIndex = normalized.lastIndexOf("```", closingFenceIndex - 1)
        if (openingFenceIndex == -1) {
            return null
        }

        val openingLineEnd = normalized.indexOf('\n', openingFenceIndex)
        if (openingLineEnd == -1 || openingLineEnd >= closingFenceIndex) {
            return null
        }

        val language = normalized.substring(openingFenceIndex + 3, openingLineEnd).trim().ifBlank { null }
        val code = normalized.substring(openingLineEnd + 1, closingFenceIndex).trim()
        if (code.isBlank()) {
            return null
        }

        val closingLineEnd = normalized.indexOf('\n', closingFenceIndex)
        var removeEnd = if (closingLineEnd == -1) normalized.length else closingLineEnd + 1
        val trailing = if (removeEnd <= normalized.length) normalized.substring(removeEnd) else ""

        var startLine: Int? = null
        var endLine: Int? = null
        val locationMatch = Regex(
            "^\\s*\\*\\*Location\\*\\*:\\s*Lines\\s*(\\d+)\\s*-\\s*(\\d+)\\s*\\s*"
        ).find(trailing)
        if (locationMatch != null) {
            startLine = locationMatch.groupValues[1].toIntOrNull()
            endLine = locationMatch.groupValues[2].toIntOrNull()
            removeEnd += locationMatch.range.last + 1
        }

        val details = normalized.removeRange(openingFenceIndex, removeEnd).trim()
        return SnippetExtraction(code, language, details, startLine, endLine)
    }


    private fun wrapHtml(bodyHtml: String): String {
        return "<html><body>$bodyHtml</body></html>"
    }

    private fun applyHtmlWithFallback(
        editor: JEditorPane,
        styledHtml: String,
        fallbackHtml: String,
        fallbackText: String
    ) {
        editor.contentType = "text/html"
        try {
            editor.text = styledHtml
        } catch (e: Exception) {
            thisLogger().warn("Failed to apply styled HTML, using fallback", e)
            try {
                editor.text = fallbackHtml
            } catch (fallbackError: Exception) {
                thisLogger().warn("Failed to apply fallback HTML, using plain text", fallbackError)
                editor.contentType = "text/plain"
                editor.text = fallbackText
            }
        }
    }

    private class StoragePathDialog(
        project: Project,
        initialPath: String
    ) : DialogWrapper(project) {
        private val pathField = TextFieldWithBrowseButton()

        var storagePath: String = initialPath
            private set

        init {
            title = "Quick Note Storage"
            pathField.text = initialPath
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                title = "Select Storage Directory"
            }
            pathField.addBrowseFolderListener(
                "Storage Directory",
                "Select where Quick Note stores notes.",
                project,
                descriptor
            )
            setResizable(true)
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Storage directory:", pathField, 1, false)
                .panel
            val preferred = panel.preferredSize
            val targetWidth = JBUI.scale(600)
            panel.preferredSize = Dimension(targetWidth, preferred.height)
            panel.minimumSize = Dimension(targetWidth, preferred.height)
            return panel
        }

        override fun doValidate(): ValidationInfo? {
            return if (normalizeStoragePath(pathField.text) == null) {
                ValidationInfo("Storage directory cannot be empty.", pathField)
            } else {
                null
            }
        }

        override fun doOKAction() {
            val normalized = normalizeStoragePath(pathField.text)
            if (normalized == null) {
                setErrorText("Storage directory cannot be empty.", pathField)
                return
            }
            storagePath = normalized
            super.doOKAction()
        }

        private fun normalizeStoragePath(rawPath: String): String? {
            val trimmed = rawPath.trim()
            if (trimmed.isBlank()) {
                return null
            }
            val expanded = if (trimmed.startsWith("~")) {
                val userHome = System.getProperty("user.home").orEmpty()
                if (userHome.isBlank()) trimmed else userHome + trimmed.removePrefix("~")
            } else {
                trimmed
            }
            return try {
                Paths.get(expanded).toAbsolutePath().normalize().toString()
            } catch (e: Exception) {
                null
            }
        }
    }
}
