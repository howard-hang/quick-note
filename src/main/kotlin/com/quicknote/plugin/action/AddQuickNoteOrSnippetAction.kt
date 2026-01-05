package com.quicknote.plugin.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.thisLogger
import com.quicknote.plugin.ui.dialog.AddNoteDialog
import com.quicknote.plugin.ui.dialog.AddSnippetDialog

/**
 * Action to add a quick note or snippet based on selection state.
 */
class AddQuickNoteOrSnippetAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectionModel = editor.selectionModel
        val file = e.getData(CommonDataKeys.PSI_FILE)?.virtualFile

        if (selectionModel.hasSelection()) {
            val selectedText = selectionModel.selectedText ?: return
            val virtualFile = file ?: return
            val document = editor.document
            val startOffset = selectionModel.selectionStart
            val endOffset = selectionModel.selectionEnd
            val startLine = document.getLineNumber(startOffset) + 1
            val endLine = document.getLineNumber(endOffset) + 1

            thisLogger().debug("AddQuickNoteOrSnippetAction triggered for lines $startLine-$endLine")

            AddSnippetDialog(
                project = project,
                file = virtualFile,
                selectedCode = selectedText,
                startLine = startLine,
                endLine = endLine
            ).show()
            return
        }

        thisLogger().debug("AddQuickNoteOrSnippetAction triggered with no selection")
        AddNoteDialog(project, file).show()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = project != null && editor != null
    }
}
