package com.quicknote.plugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.quicknote.plugin.service.FileService
import com.quicknote.plugin.ui.toolwindow.QuickNoteToolWindowContent
import com.quicknote.plugin.ui.toolwindow.QuickNoteToolWindowFactory

/**
 * Action to show notes for the selected file in the Quick Notes tool window.
 */
class ShowNotesForFileAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = getSelectedFile(e) ?: return

        val relativePath = FileService.getInstance().getRelativePath(project, file)
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(QuickNoteToolWindowFactory.TOOL_WINDOW_ID)
            ?: return

        toolWindow.activate {
            val content = toolWindow.contentManager.contents.firstOrNull()
            val toolWindowContent = content?.getUserData(QuickNoteToolWindowContent.CONTENT_KEY)
            toolWindowContent?.showNotesForFile(relativePath)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = getSelectedFileForUpdate(e)
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = project != null && editor != null && file != null && !file.isDirectory
    }

    private fun getSelectedFile(e: AnActionEvent): VirtualFile? {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (files != null && files.size != 1) {
            return null
        }
        return files?.firstOrNull() ?: e.getData(CommonDataKeys.VIRTUAL_FILE)
    }

    private fun getSelectedFileForUpdate(e: AnActionEvent): VirtualFile? {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (files != null && files.size != 1) {
            return null
        }
        return files?.firstOrNull() ?: e.getData(CommonDataKeys.VIRTUAL_FILE)
    }
}
