package com.quicknote.plugin.ui.dialog

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.quicknote.plugin.model.Note
import com.quicknote.plugin.model.NoteConstants
import com.quicknote.plugin.model.NoteMetadata
import com.quicknote.plugin.model.NoteType
import com.quicknote.plugin.settings.QuickNoteSettings
import com.quicknote.plugin.service.FileService
import com.quicknote.plugin.service.GitBranchService
import com.quicknote.plugin.service.NoteService
import com.quicknote.plugin.service.SearchService
import com.quicknote.plugin.ui.dialog.CommonTagsDialog
import java.awt.BorderLayout
import java.awt.Font
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Dialog for adding a code snippet note
 */
class AddSnippetDialog(
    private val project: Project,
    private val file: VirtualFile,
    private val selectedCode: String,
    private val startLine: Int,
    private val endLine: Int
) : DialogWrapper(project) {

    private val titleField = JBTextField(40)
    private val codePreview = JBTextArea(10, 40)
    private val notesArea = JBTextArea(5, 40)
    private val tagsField = JBTextField(40)
    private val branchField = JBTextField(40)
    private val filePathField = JBTextField(40)
    private val commonTagsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
    private val commonTagCheckboxes = mutableListOf<JBCheckBox>()
    private val commonTagsRow = JPanel(BorderLayout(8, 0))
    private val branchRow = JPanel(BorderLayout(6, 0))
    private val editCommonTagsButton = JButton("Edit")
    private val useCurrentBranchButton = JButton("Use Current")
    private var isSyncingTags = false

    private val language = FileService.getInstance().detectLanguage(file)

    init {
        title = "Add Quick Snippet"
        init()
        setupFields()
    }

    private fun setupFields() {
        // Auto-generate title
        val fileName = file.name
        titleField.text = "Code snippet from $fileName"

        // Code preview (read-only)
        codePreview.text = selectedCode
        codePreview.isEditable = false
        codePreview.font = Font("Monospaced", Font.PLAIN, 12)

        // Notes area
        notesArea.lineWrap = true
        notesArea.wrapStyleWord = true

        // File path with line numbers
        val relativePath = FileService.getInstance().getRelativePath(project, file)
        filePathField.text = "$relativePath:$startLine-$endLine"
        filePathField.isEditable = false

        commonTagsRow.add(commonTagsPanel, BorderLayout.CENTER)
        editCommonTagsButton.addActionListener { showCommonTagsEditor() }
        commonTagsRow.add(editCommonTagsButton, BorderLayout.EAST)
        refreshCommonTags()

        tagsField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateCommonTagSelection()
            override fun removeUpdate(e: DocumentEvent?) = updateCommonTagSelection()
            override fun changedUpdate(e: DocumentEvent?) = updateCommonTagSelection()
        })

        branchField.text = NoteConstants.DEFAULT_GIT_BRANCH
        branchField.emptyText.text = "Current branch: resolving..."
        updateBranchFromCurrent(forceRefresh = false)
        useCurrentBranchButton.addActionListener {
            updateBranchFromCurrent(forceRefresh = true)
        }
        branchRow.add(branchField, BorderLayout.CENTER)
        branchRow.add(useCurrentBranchButton, BorderLayout.EAST)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(5)

        // Title
        panel.add(JBLabel("Title:"), gbc)
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(titleField, gbc)

        // Code preview
        gbc.gridx = 0
        gbc.gridy++
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JBLabel("Selected Code:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 0.5
        panel.add(JScrollPane(codePreview), gbc)

        // Content
        gbc.gridx = 0
        gbc.gridy++
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JBLabel("Content:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 0.3
        panel.add(JScrollPane(notesArea), gbc)

        // Tags
        gbc.gridx = 0
        gbc.gridy++
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.anchor = GridBagConstraints.WEST
        panel.add(JBLabel("Tags:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(tagsField, gbc)

        gbc.gridx = 0
        gbc.gridy++
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JBLabel("Common Tags:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(commonTagsRow, gbc)

        // Git branch
        gbc.gridx = 0
        gbc.gridy++
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JBLabel("Git Branch:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(branchRow, gbc)

        // File path
        gbc.gridx = 0
        gbc.gridy++
        panel.add(JBLabel("File Path:"), gbc)

        gbc.gridx = 1
        panel.add(filePathField, gbc)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(panel, BorderLayout.CENTER)
        wrapper.preferredSize = JBUI.size(700, 500)

        return wrapper
    }

    override fun doOKAction() {
        val title = titleField.text.trim()
        val content = notesArea.text.trim()

        if (title.isBlank()) {
            Messages.showErrorDialog(project, "Title is required", "Validation Error")
            return  // Don't close dialog, let user fix the error
        }

        if (content.isBlank()) {
            Messages.showErrorDialog(project, "Content is required", "Validation Error")
            return  // Don't close dialog, let user fix the error
        }

        val tags = parseTags(tagsField.text)
        val gitBranch = branchField.text.trim()
        if (gitBranch.isBlank()) {
            Messages.showErrorDialog(project, "Git branch is required", "Validation Error")
            return  // Don't close dialog, let user fix the error
        }

        // Create note with metadata
        val note = Note(
            id = "",
            title = title,
            content = content,
            tags = tags,
            filePath = FileService.getInstance().getRelativePath(project, file),
            projectName = project.name,
            type = NoteType.SNIPPET,
            createdAt = 0,
            modifiedAt = 0,
            gitBranch = gitBranch,
            metadata = NoteMetadata(
                language = language,
                startLine = startLine,
                endLine = endLine,
                snippet = selectedCode
            )
        )

        // Save note
        try {
            val result = NoteService.getInstance(project).saveNote(note)
            result.onSuccess { savedNote ->
                try {
                    // Index for search
                    SearchService.getInstance(project).indexNote(savedNote)
                } catch (e: Exception) {
                    // Log search index error but don't fail the save
                    thisLogger().error("Failed to index snippet for search", e)
                }

                QuickNoteSettings.getInstance().recordCommonTags(tags)

                // Close dialog only after successful save
                super.doOKAction()

                // Show success message after closing
                Messages.showInfoMessage(project, "Snippet saved successfully!", "Success")
            }.onFailure { error ->
                thisLogger().error("Failed to save snippet", error)
                Messages.showErrorDialog(
                    project,
                    "Failed to save snippet:\n${error.message ?: error.javaClass.simpleName}\n\nPlease check the IDE logs for details.",
                    "Error"
                )
                // Don't close dialog on failure
            }
        } catch (e: Exception) {
            thisLogger().error("Unexpected error while saving snippet", e)
            Messages.showErrorDialog(
                project,
                "Unexpected error:\n${e.message ?: e.javaClass.simpleName}\n\nPlease check the IDE logs for details.",
                "Error"
            )
        }
    }

    override fun doCancelAction() {
        // Close dialog when Cancel button is clicked
        super.doCancelAction()
    }

    private fun showCommonTagsEditor() {
        val dialog = CommonTagsDialog(project)
        if (dialog.showAndGet()) {
            refreshCommonTags()
        }
    }

    private fun refreshCommonTags() {
        commonTagsPanel.removeAll()
        commonTagCheckboxes.clear()
        val commonTags = QuickNoteSettings.getInstance().getCommonTags(4)
        commonTags.forEach { tag ->
            val checkBox = JBCheckBox(tag)
            checkBox.addActionListener { applyCommonTagsToField() }
            commonTagCheckboxes.add(checkBox)
            commonTagsPanel.add(checkBox)
        }
        updateCommonTagSelection()
        commonTagsPanel.revalidate()
        commonTagsPanel.repaint()
    }

    private fun updateCommonTagSelection() {
        if (isSyncingTags) {
            return
        }
        val tags = parseTags(tagsField.text).toSet()
        commonTagCheckboxes.forEach { checkBox ->
            checkBox.isSelected = tags.contains(checkBox.text)
        }
    }

    private fun applyCommonTagsToField() {
        val existingTags = parseTags(tagsField.text)
        val commonTags = commonTagCheckboxes.map { it.text }
        val baseTags = existingTags.filterNot { it in commonTags }
        val selectedTags = commonTagCheckboxes.filter { it.isSelected }.map { it.text }
        val merged = LinkedHashSet<String>()
        baseTags.forEach { merged.add(it) }
        selectedTags.forEach { merged.add(it) }
        isSyncingTags = true
        try {
            tagsField.text = merged.joinToString(", ")
        } finally {
            isSyncingTags = false
        }
    }

    private fun parseTags(raw: String): List<String> {
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun updateBranchFromCurrent(forceRefresh: Boolean) {
        useCurrentBranchButton.isEnabled = false
        ApplicationManager.getApplication().executeOnPooledThread {
            val branch = GitBranchService.getInstance(project).getCurrentBranchOrDefault(forceRefresh)
            SwingUtilities.invokeLater {
                val currentText = branchField.text.trim()
                if (forceRefresh || currentText.isBlank() || currentText == NoteConstants.DEFAULT_GIT_BRANCH) {
                    branchField.text = branch
                }
                branchField.emptyText.text = "Current branch: $branch"
                useCurrentBranchButton.isEnabled = true
            }
        }
    }
}
