package com.quicknote.plugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.thisLogger
import com.quicknote.plugin.ui.dialog.AddNoteDialog

/**
 * Action to add a quick text note
 * Shown in editor context menu when NO text is selected
 */
class AddQuickNoteAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE)?.virtualFile

        thisLogger().debug("AddQuickNoteAction triggered")

        // Open add note dialog
        val dialog = AddNoteDialog(project, file)
        dialog.show()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false

        // Show this action only when:
        // 1. Project is open
        // 2. Editor is available
        // 3. No text is selected
        e.presentation.isEnabledAndVisible = project != null &&
                editor != null &&
                !hasSelection
    }
}
