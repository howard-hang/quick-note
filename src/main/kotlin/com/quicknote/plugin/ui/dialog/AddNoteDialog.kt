package com.quicknote.plugin.ui.dialog

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
import com.quicknote.plugin.model.NoteType
import com.quicknote.plugin.settings.QuickNoteSettings
import com.quicknote.plugin.service.FileService
import com.quicknote.plugin.service.NoteService
import com.quicknote.plugin.service.SearchService
import com.quicknote.plugin.ui.dialog.CommonTagsDialog
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Dialog for adding a quick text note
 */
class AddNoteDialog(
    private val project: Project,
    private val file: VirtualFile?
) : DialogWrapper(project) {

    private val titleField = JBTextField(40)
    private val contentArea = JBTextArea(10, 40)
    private val tagsField = JBTextField(40)
    private val filePathField = JBTextField(40)
    private val commonTagsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
    private val commonTagCheckboxes = mutableListOf<JBCheckBox>()
    private val commonTagsRow = JPanel(BorderLayout(8, 0))
    private val editCommonTagsButton = JButton("Edit")
    private var isSyncingTags = false

    init {
        title = "Add Quick Note"
        init()
        setupFields()
    }

    private fun setupFields() {
        // Set file path if available
        if (file != null) {
            val relativePath = FileService.getInstance().getRelativePath(project, file)
            filePathField.text = relativePath
        }

        filePathField.isEditable = false
        contentArea.lineWrap = true
        contentArea.wrapStyleWord = true

        commonTagsRow.add(commonTagsPanel, BorderLayout.CENTER)
        editCommonTagsButton.addActionListener { showCommonTagsEditor() }
        commonTagsRow.add(editCommonTagsButton, BorderLayout.EAST)
        refreshCommonTags()

        tagsField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateCommonTagSelection()
            override fun removeUpdate(e: DocumentEvent?) = updateCommonTagSelection()
            override fun changedUpdate(e: DocumentEvent?) = updateCommonTagSelection()
        })
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(5)

        // Title
        panel.add(JBLabel("Title: *"), gbc)
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(titleField, gbc)

        // Content
        gbc.gridx = 0
        gbc.gridy++
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JBLabel("Content: *"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        panel.add(JScrollPane(contentArea), gbc)

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

        // File path
        gbc.gridx = 0
        gbc.gridy++
        panel.add(JBLabel("File Path:"), gbc)

        gbc.gridx = 1
        panel.add(filePathField, gbc)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(panel, BorderLayout.CENTER)
        wrapper.preferredSize = JBUI.size(600, 400)

        return wrapper
    }

    override fun doOKAction() {
        // Validate input
        val title = titleField.text.trim()
        val content = contentArea.text.trim()

        if (title.isBlank()) {
            Messages.showErrorDialog(project, "Title is required", "Validation Error")
            return  // Don't close dialog, let user fix the error
        }

        if (content.isBlank()) {
            Messages.showErrorDialog(project, "Content is required", "Validation Error")
            return  // Don't close dialog, let user fix the error
        }

        val tags = parseTags(tagsField.text)

        // Create note
        val note = Note(
            id = "",  // Will be generated by service
            title = title,
            content = content,
            tags = tags,
            filePath = filePathField.text,
            projectName = project.name,
            type = NoteType.NOTE,
            createdAt = 0,
            modifiedAt = 0
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
                    thisLogger().error("Failed to index note for search", e)
                }

                QuickNoteSettings.getInstance().recordCommonTags(tags)

                // Close dialog only after successful save
                super.doOKAction()

                // Show success message after closing
                Messages.showInfoMessage(project, "Note saved successfully!", "Success")
            }.onFailure { error ->
                thisLogger().error("Failed to save note", error)
                Messages.showErrorDialog(
                    project,
                    "Failed to save note:\n${error.message ?: error.javaClass.simpleName}\n\nPlease check the IDE logs for details.",
                    "Error"
                )
                // Don't close dialog on failure
            }
        } catch (e: Exception) {
            thisLogger().error("Unexpected error while saving note", e)
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
}
