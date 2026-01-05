package com.quicknote.plugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.thisLogger
import com.quicknote.plugin.ui.dialog.AddSnippetDialog

/**
 * Action to add a code snippet note
 * Shown in editor context menu when text IS selected
 */
class AddQuickSnippetAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE)?.virtualFile ?: return

        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText ?: return

        // Get line numbers
        val document = editor.document
        val startOffset = selectionModel.selectionStart
        val endOffset = selectionModel.selectionEnd
        val startLine = document.getLineNumber(startOffset) + 1  // 1-based
        val endLine = document.getLineNumber(endOffset) + 1      // 1-based

        thisLogger().debug("AddQuickSnippetAction triggered for lines $startLine-$endLine")

        // Open add snippet dialog
        val dialog = AddSnippetDialog(
            project = project,
            file = file,
            selectedCode = selectedText,
            startLine = startLine,
            endLine = endLine
        )
        dialog.show()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false

        // Show this action only when:
        // 1. Project is open
        // 2. Editor is available
        // 3. Text IS selected
        e.presentation.isEnabledAndVisible = project != null &&
                editor != null &&
                hasSelection
    }
}
