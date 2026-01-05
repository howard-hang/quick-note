package com.quicknote.plugin.ui.dialog

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.quicknote.plugin.model.Note
import com.quicknote.plugin.model.NoteType
import com.quicknote.plugin.settings.QuickNoteSettings
import com.quicknote.plugin.service.NoteService
import com.quicknote.plugin.service.SearchService
import java.awt.BorderLayout
import java.awt.Font
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
 * Dialog for editing an existing note.
 */
class EditNoteDialog(
    private val project: Project,
    private val note: Note
) : DialogWrapper(project) {

    private val titleField = JBTextField(40)
    private val contentArea = JBTextArea(10, 40)
    private val codePreview = JBTextArea(10, 40)
    private val tagsField = JBTextField(40)
    private val filePathField = JBTextField(40)
    private val commonTagsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
    private val commonTagCheckboxes = mutableListOf<JBCheckBox>()
    private val commonTagsRow = JPanel(BorderLayout(8, 0))
    private val editCommonTagsButton = JButton("Edit")
    private var isSyncingTags = false

    var updatedNote: Note? = null
        private set

    init {
        title = if (note.type == NoteType.SNIPPET) "Edit Snippet" else "Edit Note"
        init()
        setupFields()
    }

    private fun setupFields() {
        titleField.text = note.title
        contentArea.text = note.content
        contentArea.lineWrap = true
        contentArea.wrapStyleWord = true

        tagsField.text = note.tags.joinToString(", ")

        filePathField.text = buildFilePathDisplay(note)
        filePathField.isEditable = false

        codePreview.text = note.metadata.snippet?.trim().orEmpty().ifBlank { "Snippet not available." }
        codePreview.isEditable = false
        codePreview.font = Font("Monospaced", Font.PLAIN, 12)

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
        panel.add(JBLabel("Title:"), gbc)
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(titleField, gbc)

        if (note.type == NoteType.SNIPPET) {
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
            gbc.weighty = 1.0
            panel.add(JScrollPane(codePreview), gbc)
        }

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
        gbc.weighty = 0.5
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
        wrapper.preferredSize = if (note.type == NoteType.SNIPPET) {
            JBUI.size(700, 500)
        } else {
            JBUI.size(600, 420)
        }

        return wrapper
    }

    override fun doOKAction() {
        val title = titleField.text.trim()
        val content = contentArea.text.trim()

        if (title.isBlank()) {
            Messages.showErrorDialog(project, "Title is required", "Validation Error")
            return
        }

        if (content.isBlank()) {
            Messages.showErrorDialog(project, "Content is required", "Validation Error")
            return
        }

        val tags = parseTags(tagsField.text)
        val updated = note.copy(
            title = title,
            content = content,
            tags = tags
        )

        try {
            val result = NoteService.getInstance(project).updateNote(updated)
            result.onSuccess { savedNote ->
                try {
                    SearchService.getInstance(project).updateIndex(savedNote)
                } catch (e: Exception) {
                    thisLogger().error("Failed to update search index", e)
                }

                QuickNoteSettings.getInstance().recordCommonTags(tags)
                updatedNote = savedNote
                super.doOKAction()

                Messages.showInfoMessage(project, "Note updated successfully!", "Success")
            }.onFailure { error ->
                thisLogger().error("Failed to update note", error)
                Messages.showErrorDialog(
                    project,
                    "Failed to update note:\n${error.message ?: error.javaClass.simpleName}\n\nPlease check the IDE logs for details.",
                    "Error"
                )
            }
        } catch (e: Exception) {
            thisLogger().error("Unexpected error while updating note", e)
            Messages.showErrorDialog(
                project,
                "Unexpected error:\n${e.message ?: e.javaClass.simpleName}\n\nPlease check the IDE logs for details.",
                "Error"
            )
        }
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

    private fun buildFilePathDisplay(note: Note): String {
        val start = note.metadata.startLine
        val end = note.metadata.endLine
        return if (start != null && end != null) {
            "${note.filePath}:$start-$end"
        } else {
            note.filePath
        }
    }
}
