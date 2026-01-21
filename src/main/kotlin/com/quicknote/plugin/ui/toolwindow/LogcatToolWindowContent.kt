package com.quicknote.plugin.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.quicknote.plugin.service.LogcatRecorderService
import com.quicknote.plugin.settings.QuickNoteSettings
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Embedded panel for recording Android logcat logs.
 */
class LogcatPanel(private val project: Project) : Disposable {

    private val recorderService = LogcatRecorderService.getInstance(project)
    private val settings = QuickNoteSettings.getInstance()

    private val pathField = TextFieldWithBrowseButton()
    private val statusLabel = JBLabel()
    private val fileLabel = JBLabel()
    private val toggleButton = JButton("Start Recording", AllIcons.Actions.Execute)
    private val openFolderButton = JButton("Open Folder")

    val component: JComponent by lazy {
        createUI()
    }

    init {
        configurePathField()
        configureActions()
        configureFileLabel()
        updateUiState()
    }

    private fun createUI(): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 8, 8, 4)

        val titleLabel = JBLabel("Logcat Recorder").apply {
            font = font.deriveFont(font.style or java.awt.Font.BOLD)
            border = JBUI.Borders.emptyBottom(4)
        }

        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Log directory:", pathField, 1, false)
            .addLabeledComponent("Status:", statusLabel, 1, false)
            .addLabeledComponent("Recent file:", fileLabel, 1, false)
            .panel
        val contentPanel = JBPanel<JBPanel<*>>(BorderLayout())
        contentPanel.add(formPanel, BorderLayout.CENTER)
        contentPanel.add(createButtonPanel(), BorderLayout.SOUTH)

        panel.add(titleLabel, BorderLayout.NORTH)
        panel.add(contentPanel, BorderLayout.CENTER)

        return panel
    }

    private fun createButtonPanel(): JComponent {
        return JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            add(toggleButton)
            add(openFolderButton)
        }
    }

    private fun configurePathField() {
        pathField.text = settings.logcatStoragePath
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = "Select Log Directory"
        }
        pathField.addBrowseFolderListener(
            "Log Directory",
            "Select where logcat logs are stored.",
            project,
            descriptor
        )
    }

    private fun configureActions() {
        toggleButton.addActionListener {
            if (recorderService.isRecording()) {
                recorderService.stopRecording()
                    .onSuccess {
                        updateUiState()
                        Messages.showInfoMessage(
                            project,
                            "Logcat recording stopped.",
                            "Logcat Recorder"
                        )
                    }
                    .onFailure { error ->
                        Messages.showErrorDialog(
                            project,
                            error.message ?: "Failed to stop logcat recording.",
                            "Logcat Recorder"
                        )
                    }
            } else {
                val logDir = resolveLogDirectory() ?: return@addActionListener
                recorderService.startRecording(logDir)
                    .onSuccess { logFile ->
                        updateUiState()
                        Messages.showInfoMessage(
                            project,
                            "Logcat recording started.\n\n${logFile.toAbsolutePath()}",
                            "Logcat Recorder"
                        )
                    }
                    .onFailure { error ->
                        Messages.showErrorDialog(
                            project,
                            error.message ?: "Failed to start logcat recording.",
                            "Logcat Recorder"
                        )
                    }
            }
        }

        openFolderButton.addActionListener {
            val logDir = resolveLogDirectory() ?: return@addActionListener
            try {
                Files.createDirectories(logDir)
                RevealFileAction.openFile(logDir.toFile())
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to open log directory: ${e.message}",
                    "Logcat Recorder"
                )
            }
        }
    }

    private fun configureFileLabel() {
        fileLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        fileLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2 || !SwingUtilities.isLeftMouseButton(e)) {
                    return
                }
                val logFile = recorderService.getCurrentLogFile()
                if (logFile == null) {
                    Messages.showWarningDialog(project, "No log file available.", "Logcat Recorder")
                    return
                }
                try {
                    RevealFileAction.openFile(logFile.toFile())
                } catch (ex: Exception) {
                    Messages.showErrorDialog(
                        project,
                        "Failed to open log file: ${ex.message}",
                        "Logcat Recorder"
                    )
                }
            }
        })
    }

    private fun resolveLogDirectory(): Path? {
        val normalized = normalizeStoragePath(pathField.text)
        if (normalized == null) {
            Messages.showErrorDialog(
                project,
                "Log directory cannot be empty.",
                "Logcat Recorder"
            )
            return null
        }
        settings.logcatStoragePath = normalized
        pathField.text = normalized
        return Paths.get(normalized)
    }

    private fun updateUiState() {
        val recording = recorderService.isRecording()
        SwingUtilities.invokeLater {
            statusLabel.text = if (recording) "Recording" else "Idle"
            statusLabel.foreground = if (recording) {
                JBColor(0x2E7D32, 0x81C784)
            } else {
                JBColor.GRAY
            }

            val logFile = recorderService.getCurrentLogFile()
            if (logFile != null) {
                fileLabel.text = logFile.fileName.toString()
                fileLabel.toolTipText = logFile.toAbsolutePath().toString()
            } else {
                fileLabel.text = "-"
                fileLabel.toolTipText = null
            }

            toggleButton.text = if (recording) "Stop Recording" else "Start Recording"
            toggleButton.icon = if (recording) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
        }
    }

    private fun normalizeStoragePath(rawPath: String): String? {
        val trimmed = rawPath.trim()
        if (trimmed.isBlank()) {
            return null
        }
        val expanded = if (trimmed.startsWith("~")) {
            val userHome = System.getProperty("user.home").orEmpty()
            if (userHome.isBlank()) trimmed else userHome + trimmed.removePrefix("~")
        } else {
            trimmed
        }
        return try {
            Paths.get(expanded).toAbsolutePath().normalize().toString()
        } catch (e: Exception) {
            null
        }
    }

    override fun dispose() {
        // No-op; recorder service is disposed separately with the project.
    }
}
