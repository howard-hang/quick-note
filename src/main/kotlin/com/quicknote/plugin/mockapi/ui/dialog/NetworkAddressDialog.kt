package com.quicknote.plugin.mockapi.ui.dialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*

/**
 * Dialog to display all network addresses where the Mock API server is accessible
 */
class NetworkAddressDialog(
    private val project: Project,
    private val addresses: List<String>
) : DialogWrapper(project) {

    init {
        title = "Mock API Network Addresses"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(10)

        // Info label
        val infoLabel = JBLabel("The Mock API server is accessible at the following addresses:")
        infoLabel.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(infoLabel)
        panel.add(Box.createVerticalStrut(10))

        // Address list
        addresses.forEach { address ->
            val addressPanel = createAddressPanel(address)
            addressPanel.alignmentX = Component.LEFT_ALIGNMENT
            panel.add(addressPanel)
            panel.add(Box.createVerticalStrut(5))
        }

        if (addresses.isEmpty()) {
            val noAddressLabel = JBLabel("No network addresses found")
            noAddressLabel.foreground = Color.GRAY
            noAddressLabel.alignmentX = Component.LEFT_ALIGNMENT
            panel.add(noAddressLabel)
        }

        // Instructions
        panel.add(Box.createVerticalStrut(10))
        val separator = JSeparator()
        separator.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(separator)
        panel.add(Box.createVerticalStrut(10))

        val instructionsLabel = JBLabel("<html><b>Usage:</b><br>" +
                "• Use any address above to access the Mock API from devices on the same network<br>" +
                "• Localhost addresses work only on this computer<br>" +
                "• LAN addresses (192.168.x.x) work on the local network<br>" +
                "• Example curl command: <code>curl http://[address]/api/your-endpoint</code></html>")
        instructionsLabel.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(instructionsLabel)

        panel.preferredSize = Dimension(JBUI.scale(600), JBUI.scale(300))
        return panel
    }

    private fun createAddressPanel(address: String): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BorderLayout(10, 0)
        panel.border = JBUI.Borders.empty(5, 10)

        // Address label
        val addressLabel = JBLabel(address)
        addressLabel.font = addressLabel.font.deriveFont(Font.BOLD)
        panel.add(addressLabel, BorderLayout.CENTER)

        // Copy button
        val copyButton = JButton("Copy", AllIcons.Actions.Copy)
        copyButton.addActionListener {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(address), null)
            Messages.showInfoMessage(project, "Address copied to clipboard: $address", "Copied")
        }
        panel.add(copyButton, BorderLayout.EAST)

        return panel
    }

    override fun createActions(): Array<Action> {
        // Only show Close button (no OK/Cancel)
        return arrayOf(cancelAction.apply { putValue(Action.NAME, "Close") })
    }
}
