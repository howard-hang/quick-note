package com.quicknote.plugin.mockapi.ui.dialog

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.quicknote.plugin.mockapi.model.MockApiConfig
import com.quicknote.plugin.mockapi.model.MockApiConstants
import com.quicknote.plugin.mockapi.service.MockApiService
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Dialog for configuring Mock API server settings
 */
class MockApiSettingsDialog(private val project: Project) : DialogWrapper(project) {

    private val service = MockApiService.getInstance(project)
    private val config = service.getConfig()

    private val portField = JBTextField(10)
    private val imagePathField = TextFieldWithBrowseButton()
    private val enableCorsCheckbox = JBCheckBox("Enable CORS (Cross-Origin Resource Sharing)", true)
    private val enableLoggingCheckbox = JBCheckBox("Enable Request Logging", true)

    init {
        title = "Mock API Server Settings"
        init()
        setupFields()
    }

    override fun createCenterPanel(): JComponent {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = "Select Image Storage Directory"
            description = "Choose a directory to store images for mock API responses"
        }

        imagePathField.addBrowseFolderListener(
            "Image Storage Directory",
            "Select directory where images will be stored",
            project,
            descriptor
        )

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Server Port:"), portField, 1, false)
            .addLabeledComponent(JBLabel("Image Storage Path:"), imagePathField, 1, false)
            .addComponent(enableCorsCheckbox, 1)
            .addComponent(enableLoggingCheckbox, 1)
            .addComponentFillVertically(JBLabel(""), 0)
            .panel

        panel.preferredSize = Dimension(JBUI.scale(500), JBUI.scale(200))
        return panel
    }

    private fun setupFields() {
        portField.text = config.port.toString()

        val imagePath = config.imageStoragePath.ifBlank {
            service.getImageStoragePath()
        }
        imagePathField.text = imagePath

        enableCorsCheckbox.isSelected = config.enableCors
        enableLoggingCheckbox.isSelected = config.enableLogging
    }

    override fun doValidate(): ValidationInfo? {
        // Validate port
        val port = portField.text.trim().toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            return ValidationInfo("Port must be between 1 and 65535", portField)
        }

        // Validate image path if provided
        val imagePath = imagePathField.text.trim()
        if (imagePath.isNotBlank() && !service.validateImagePath(imagePath)) {
            return ValidationInfo("Image path must be a valid directory", imagePathField)
        }

        return null
    }

    override fun doOKAction() {
        val validation = doValidate()
        if (validation != null) {
            setErrorText(validation.message, validation.component)
            return
        }

        val newConfig = MockApiConfig(
            port = portField.text.trim().toInt(),
            host = MockApiConstants.DEFAULT_HOST,
            imageStoragePath = imagePathField.text.trim(),
            enableCors = enableCorsCheckbox.isSelected,
            enableLogging = enableLoggingCheckbox.isSelected
        )

        val result = service.updateConfig(newConfig)
        result.onSuccess {
            // Warn if server is running
            if (service.isServerRunning()) {
                Messages.showInfoMessage(
                    project,
                    "Settings saved. Please restart the server for changes to take effect.",
                    "Settings Updated"
                )
            }
            super.doOKAction()
        }.onFailure { error ->
            Messages.showErrorDialog(
                project,
                "Failed to save settings: ${error.message}",
                "Error"
            )
        }
    }
}
