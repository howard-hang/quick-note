package com.quicknote.plugin.ui.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.quicknote.plugin.settings.QuickNoteSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class CommonTagsDialog(project: Project) : DialogWrapper(project) {
    private val listModel = DefaultListModel<String>()
    private val tagList = JBList(listModel)
    private val inputField = JBTextField(20)
    private val addButton = JButton("Add")
    private val removeButton = JButton("Remove Selected")

    init {
        title = "Edit Common Tags"
        setResizable(true)
        loadTags()
        setupActions()
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.border = JBUI.Borders.empty(10)

        tagList.visibleRowCount = 8
        panel.add(JBScrollPane(tagList), BorderLayout.CENTER)

        val actionPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        actionPanel.add(JBLabel("Tag:"))
        actionPanel.add(inputField)
        actionPanel.add(addButton)
        actionPanel.add(removeButton)

        panel.add(actionPanel, BorderLayout.SOUTH)
        panel.preferredSize = JBUI.size(500, 260)
        return panel
    }

    override fun doOKAction() {
        val tags = (0 until listModel.size()).map { listModel.getElementAt(it) }
        QuickNoteSettings.getInstance().setStoredTags(tags)
        super.doOKAction()
    }

    private fun loadTags() {
        QuickNoteSettings.getInstance().getStoredTags().forEach { listModel.addElement(it) }
    }

    private fun setupActions() {
        addButton.addActionListener { addTagFromInput() }
        inputField.addActionListener { addTagFromInput() }
        removeButton.addActionListener { removeSelectedTags() }
    }

    private fun addTagFromInput() {
        val tag = inputField.text.trim()
        if (tag.isBlank()) {
            return
        }
        if (!containsTag(tag)) {
            listModel.addElement(tag)
        }
        inputField.text = ""
    }

    private fun removeSelectedTags() {
        val selected = tagList.selectedValuesList
        if (selected.isEmpty()) {
            return
        }
        selected.forEach { listModel.removeElement(it) }
    }

    private fun containsTag(tag: String): Boolean {
        for (index in 0 until listModel.size()) {
            if (listModel.getElementAt(index) == tag) {
                return true
            }
        }
        return false
    }
}
