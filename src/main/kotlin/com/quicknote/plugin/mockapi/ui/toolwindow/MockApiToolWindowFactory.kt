package com.quicknote.plugin.mockapi.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the Mock API tool window
 */
class MockApiToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        const val TOOL_WINDOW_ID = "Mock API"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = MockApiToolWindowContent(project)
        val contentInstance = ContentFactory.getInstance().createContent(
            content.component,
            "",
            false
        )
        toolWindow.contentManager.addContent(contentInstance)
    }

    override suspend fun isApplicableAsync(project: Project): Boolean {
        return true
    }

    override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
        // No-op for compatibility with older IDEs.
    }
}
