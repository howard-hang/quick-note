package com.quicknote.plugin.mockapi.ui.renderer

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.quicknote.plugin.mockapi.model.HttpMethod
import com.quicknote.plugin.mockapi.model.MockEndpoint
import java.awt.*
import javax.swing.*

/**
 * Custom cell renderer for endpoint list
 * Displays endpoints in a Postman-style format with HTTP method color coding
 */
class EndpointListCellRenderer : ListCellRenderer<MockEndpoint> {

    override fun getListCellRendererComponent(
        list: JList<out MockEndpoint>,
        value: MockEndpoint,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BorderLayout(8, 2)
        panel.border = JBUI.Borders.empty(4, 8)
        panel.isOpaque = true

        // Method badge
        val methodBadge = JBLabel(value.method.name)
        methodBadge.font = methodBadge.font.deriveFont(Font.BOLD, 10f)
        methodBadge.foreground = Color.WHITE
        methodBadge.isOpaque = true
        methodBadge.background = getMethodColor(value.method)
        methodBadge.border = JBUI.Borders.empty(2, 6)
        methodBadge.horizontalAlignment = JBLabel.CENTER

        val methodPanel = JBPanel<JBPanel<*>>(BorderLayout())
        methodPanel.isOpaque = false
        methodPanel.add(methodBadge, BorderLayout.NORTH)
        methodPanel.preferredSize = Dimension(60, methodPanel.preferredSize.height)

        // Content panel - two rows
        val contentPanel = JBPanel<JBPanel<*>>()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.isOpaque = false

        // First line: Path
        val pathLabel = JBLabel(value.path)
        pathLabel.font = pathLabel.font.deriveFont(Font.BOLD, 12f)
        pathLabel.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(pathLabel)

        // Add small vertical spacing
        contentPanel.add(Box.createVerticalStrut(2))

        // Second line: Name + Status
        val secondLinePanel = JBPanel<JBPanel<*>>()
        secondLinePanel.layout = FlowLayout(FlowLayout.LEFT, 4, 0)
        secondLinePanel.isOpaque = false
        secondLinePanel.alignmentX = Component.LEFT_ALIGNMENT

        val nameLabel = JBLabel(value.name)
        nameLabel.font = nameLabel.font.deriveFont(Font.PLAIN, 11f)
        secondLinePanel.add(nameLabel)

        val statusBadge = JBLabel(if (value.enabled) "●" else "○")
        statusBadge.foreground = if (value.enabled) JBColor.GREEN else JBColor.GRAY
        statusBadge.font = statusBadge.font.deriveFont(14f)
        statusBadge.toolTipText = if (value.enabled) "Enabled" else "Disabled"
        secondLinePanel.add(statusBadge)

        val statusCodeLabel = JBLabel("${value.statusCode}")
        statusCodeLabel.font = statusCodeLabel.font.deriveFont(Font.PLAIN, 10f)
        statusCodeLabel.foreground = JBColor.GRAY
        secondLinePanel.add(statusCodeLabel)

        contentPanel.add(secondLinePanel)

        panel.add(methodPanel, BorderLayout.WEST)
        panel.add(contentPanel, BorderLayout.CENTER)

        // Selection styling
        val background = if (isSelected) list.selectionBackground else list.background
        val foreground = if (isSelected) list.selectionForeground else list.foreground
        panel.background = background
        contentPanel.background = background
        pathLabel.foreground = foreground
        nameLabel.foreground = if (isSelected) foreground else JBColor.GRAY
        statusCodeLabel.foreground = if (isSelected) foreground else JBColor.GRAY

        return panel
    }

    /**
     * Get color for HTTP method (Postman-style)
     */
    private fun getMethodColor(method: HttpMethod): Color {
        return when (method) {
            HttpMethod.GET -> JBColor(0x61AFFE, 0x61AFFE)      // Blue
            HttpMethod.POST -> JBColor(0x49CC90, 0x49CC90)     // Green
            HttpMethod.PUT -> JBColor(0xFCA130, 0xFCA130)      // Orange
            HttpMethod.DELETE -> JBColor(0xF93E3E, 0xF93E3E)   // Red
            HttpMethod.PATCH -> JBColor(0x50E3C2, 0x50E3C2)    // Teal
            HttpMethod.OPTIONS -> JBColor(0x9012FE, 0x9012FE)  // Purple
            HttpMethod.HEAD -> JBColor(0x7D7D7D, 0x7D7D7D)     // Gray
        }
    }
}
