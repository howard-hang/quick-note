package com.quicknote.plugin.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.quicknote.plugin.model.LogcatConstants

/**
 * Factory for creating Logcat Recorder tool window.
 */
class LogcatToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = LogcatPanel(project)
        val content = ContentFactory.getInstance().createContent(
            toolWindowContent.component,
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    companion object {
        const val TOOL_WINDOW_ID = LogcatConstants.TOOL_WINDOW_ID
    }
}
