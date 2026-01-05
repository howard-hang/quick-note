package com.quicknote.plugin.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating Quick Notes tool window.
 */
class QuickNoteToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = QuickNoteToolWindowContent(project)
        val content = ContentFactory.getInstance().createContent(
            toolWindowContent.component,
            "",
            false
        )
        content.putUserData(QuickNoteToolWindowContent.CONTENT_KEY, toolWindowContent)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    companion object {
        const val TOOL_WINDOW_ID = "Quick Notes"
    }
}
