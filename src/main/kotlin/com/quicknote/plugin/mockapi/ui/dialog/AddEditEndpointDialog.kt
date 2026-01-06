package com.quicknote.plugin.mockapi.ui.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.quicknote.plugin.mockapi.model.HttpMethod
import com.quicknote.plugin.mockapi.model.MockEndpoint
import com.quicknote.plugin.mockapi.service.MockApiService
import kotlinx.serialization.json.Json
import java.awt.Dimension
import java.util.*
import javax.swing.JComponent
import javax.swing.JScrollPane

/**
 * Dialog for adding or editing a mock API endpoint
 */
class AddEditEndpointDialog(
    private val project: Project,
    private val endpoint: MockEndpoint? = null
) : DialogWrapper(project) {

    private val service = MockApiService.getInstance(project)

    private val nameField = JBTextField(40)
    private val pathField = JBTextField(40)
    private val methodComboBox = ComboBox(HttpMethod.values())
    private val statusCodeField = JBTextField(10)
    private val responseBodyArea = JBTextArea(15, 60)
    private val delayField = JBTextField(10)
    private val enabledCheckbox = JBCheckBox("Enabled", true)
    private val descriptionArea = JBTextArea(3, 60)

    private val json = Json { prettyPrint = true }

    init {
        title = if (endpoint == null) "Add Mock Endpoint" else "Edit Mock Endpoint"
        init()
        setupFields()
    }

    override fun createCenterPanel(): JComponent {
        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Name:"), nameField, 1, false)
            .addLabeledComponent(JBLabel("HTTP Method:"), methodComboBox, 1, false)
            .addLabeledComponent(JBLabel("Path:"), pathField, 1, false)
            .addLabeledComponent(JBLabel("Status Code:"), statusCodeField, 1, false)
            .addLabeledComponent(JBLabel("Response Delay (ms):"), delayField, 1, false)
            .addComponent(enabledCheckbox, 1)
            .addLabeledComponent(JBLabel("Description:"), JScrollPane(descriptionArea), 1, false)
            .addSeparator()
            .addLabeledComponent(JBLabel("Response Body (JSON):"), JScrollPane(responseBodyArea), 1, true)
            .panel

        panel.preferredSize = Dimension(JBUI.scale(700), JBUI.scale(600))
        return panel
    }

    private fun setupFields() {
        if (endpoint != null) {
            // Edit mode - populate fields
            nameField.text = endpoint.name
            pathField.text = endpoint.path
            methodComboBox.selectedItem = endpoint.method
            statusCodeField.text = endpoint.statusCode.toString()
            responseBodyArea.text = formatJson(endpoint.responseBody)
            delayField.text = endpoint.delay.toString()
            enabledCheckbox.isSelected = endpoint.enabled
            descriptionArea.text = endpoint.description
        } else {
            // Add mode - set defaults
            methodComboBox.selectedItem = HttpMethod.GET
            statusCodeField.text = "200"
            delayField.text = "0"
            enabledCheckbox.isSelected = true
            responseBodyArea.text = "{\n  \n}"
        }

        // Add placeholder for path
        if (pathField.text.isEmpty()) {
            pathField.text = "/api/"
        }
    }

    override fun doValidate(): ValidationInfo? {
        // Validate name
        if (nameField.text.trim().isBlank()) {
            return ValidationInfo("Name is required", nameField)
        }

        // Validate path
        val path = pathField.text.trim()
        if (path.isBlank()) {
            return ValidationInfo("Path is required", pathField)
        }
        if (!path.startsWith("/")) {
            return ValidationInfo("Path must start with /", pathField)
        }

        // Validate status code
        val statusCode = statusCodeField.text.trim().toIntOrNull()
        if (statusCode == null || statusCode < 100 || statusCode >= 600) {
            return ValidationInfo("Status code must be between 100 and 599", statusCodeField)
        }

        // Validate delay
        val delay = delayField.text.trim().toLongOrNull()
        if (delay == null || delay < 0) {
            return ValidationInfo("Delay must be a non-negative number", delayField)
        }

        // Validate JSON
        val responseBody = responseBodyArea.text.trim()
        if (responseBody.isNotBlank() && !isValidJson(responseBody)) {
            return ValidationInfo("Response body must be valid JSON", responseBodyArea)
        }

        return null
    }

    override fun doOKAction() {
        val validation = doValidate()
        if (validation != null) {
            setErrorText(validation.message, validation.component)
            return
        }

        val newEndpoint = MockEndpoint(
            id = endpoint?.id ?: "",
            name = nameField.text.trim(),
            path = pathField.text.trim(),
            method = methodComboBox.selectedItem as HttpMethod,
            statusCode = statusCodeField.text.trim().toInt(),
            responseBody = responseBodyArea.text.trim(),
            delay = delayField.text.trim().toLong(),
            enabled = enabledCheckbox.isSelected,
            description = descriptionArea.text.trim(),
            createdAt = endpoint?.createdAt ?: 0,
            modifiedAt = 0,
            projectName = project.name
        )

        val result = if (endpoint == null) {
            service.createEndpoint(newEndpoint)
        } else {
            service.updateEndpoint(newEndpoint)
        }

        result.onSuccess {
            super.doOKAction()
        }.onFailure { error ->
            Messages.showErrorDialog(
                project,
                "Failed to save endpoint: ${error.message}",
                "Error"
            )
        }
    }

    private fun isValidJson(text: String): Boolean {
        return try {
            json.parseToJsonElement(text)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun formatJson(text: String): String {
        return try {
            val element = json.parseToJsonElement(text)
            json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
        } catch (e: Exception) {
            text
        }
    }
}
