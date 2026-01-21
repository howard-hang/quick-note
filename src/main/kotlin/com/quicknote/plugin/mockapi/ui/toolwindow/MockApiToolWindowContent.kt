package com.quicknote.plugin.mockapi.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.quicknote.plugin.mockapi.model.MockEndpoint
import com.quicknote.plugin.mockapi.model.MockApiRequest
import com.quicknote.plugin.mockapi.model.HttpMethod
import com.quicknote.plugin.mockapi.service.MockApiChangeListener
import com.quicknote.plugin.mockapi.service.MockApiService
import com.quicknote.plugin.mockapi.ui.dialog.AddEditEndpointDialog
import com.quicknote.plugin.mockapi.ui.dialog.MockApiSettingsDialog
import com.quicknote.plugin.mockapi.ui.dialog.NetworkAddressDialog
import com.quicknote.plugin.mockapi.ui.renderer.EndpointListCellRenderer
import com.quicknote.plugin.ui.toolwindow.LogcatPanel
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.table.AbstractTableModel

/**
 * Main content for Mock API tool window
 * Provides Postman-style UI for managing mock API endpoints
 */
class MockApiToolWindowContent(private val project: Project) : Disposable {

    private val service = MockApiService.getInstance(project)

    // UI Components
    private val endpointListModel = DefaultListModel<MockEndpoint>()
    private val endpointList = JBList(endpointListModel)
    private val serverStatusLabel = JBLabel("Server: Stopped")
    private val networkAddressLabel = JBLabel("")
    private val startStopButton = JButton("Start Server", AllIcons.Actions.Execute)
    private val settingsButton = JButton(AllIcons.General.Settings)
    private val addEndpointButton = JButton("Add", AllIcons.General.Add)
    private val editEndpointButton = JButton("Edit", AllIcons.Actions.Edit)
    private val deleteEndpointButton = JButton("Delete", AllIcons.Actions.Cancel)
    private val copyUrlButton = JButton("Copy URL", AllIcons.Actions.Copy)
    private val showAddressesButton = JButton("Show Addresses", AllIcons.Actions.Preview)
    private val requestHistoryTableModel = RequestHistoryTableModel()
    private val requestHistoryTable = JBTable(requestHistoryTableModel)
    private val clearHistoryButton = JButton("Clear", AllIcons.Actions.GC)
    private val logcatPanel = LogcatPanel(project)

    // Detail panel components
    private val detailNameField = JBTextField()
    private val detailMethodLabel = JBLabel("")
    private val detailPathField = JBTextField()
    private val detailStatusField = JBTextField()
    private val detailResponseArea = JBTextArea()
    private val detailEnabledLabel = JBLabel("")

    val component: JComponent by lazy { createUI() }

    init {
        setupListeners()
        loadEndpoints()
        createDefaultEndpointsIfNeeded()
        updateServerStatus()
        Disposer.register(this, logcatPanel)
        Disposer.register(project, this)
    }

    private fun createUI(): JComponent {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())

        // Top: Server control panel
        mainPanel.add(createServerControlPanel(), BorderLayout.NORTH)

        // Center: Left panel (Logcat + Endpoints) + Right panel (Details + Log)
        val leftSplitter = Splitter(true, 0.35f).apply {
            firstComponent = createLogcatPanel()
            secondComponent = createEndpointListPanel()
        }

        val splitter = Splitter(false, 0.25f).apply {
            firstComponent = leftSplitter

            val rightSplitter = Splitter(false, 0.60f).apply {
                firstComponent = createEndpointDetailPanel()
                secondComponent = createRequestLogPanel()
            }
            secondComponent = rightSplitter
        }
        mainPanel.add(splitter, BorderLayout.CENTER)

        return mainPanel
    }

    private fun createServerControlPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BorderLayout()
        panel.border = JBUI.Borders.empty(8)

        // Status panel
        val statusPanel = JBPanel<JBPanel<*>>()
        statusPanel.layout = FlowLayout(FlowLayout.LEFT, 10, 0)

        serverStatusLabel.font = serverStatusLabel.font.deriveFont(Font.BOLD)
        statusPanel.add(serverStatusLabel)
        statusPanel.add(networkAddressLabel)

        panel.add(statusPanel, BorderLayout.WEST)

        // Buttons panel
        val buttonsPanel = JBPanel<JBPanel<*>>()
        buttonsPanel.layout = FlowLayout(FlowLayout.RIGHT, 5, 0)

        startStopButton.addActionListener { onStartStopServer() }
        copyUrlButton.addActionListener { onCopyUrl() }
        copyUrlButton.isEnabled = false
        showAddressesButton.addActionListener { onShowAddresses() }
        showAddressesButton.isEnabled = false
        settingsButton.toolTipText = "Server Settings"
        settingsButton.addActionListener { onSettings() }

        buttonsPanel.add(copyUrlButton)
        buttonsPanel.add(showAddressesButton)
        buttonsPanel.add(startStopButton)
        buttonsPanel.add(settingsButton)

        panel.add(buttonsPanel, BorderLayout.EAST)

        return panel
    }

    private fun createEndpointListPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 8, 8, 4)

        // Title
        val titleLabel = JBLabel("Endpoints")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
        titleLabel.border = JBUI.Borders.emptyBottom(4)

        // Toolbar
        val toolbar = JBPanel<JBPanel<*>>()
        toolbar.layout = FlowLayout(FlowLayout.LEFT, 5, 5)

        addEndpointButton.addActionListener { onAddEndpoint() }
        editEndpointButton.addActionListener { onEditEndpoint() }
        editEndpointButton.isEnabled = false
        deleteEndpointButton.addActionListener { onDeleteEndpoint() }
        deleteEndpointButton.isEnabled = false

        toolbar.add(addEndpointButton)
        toolbar.add(editEndpointButton)
        toolbar.add(deleteEndpointButton)

        // List
        endpointList.cellRenderer = EndpointListCellRenderer()
        endpointList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        endpointList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = endpointList.selectedValue
                editEndpointButton.isEnabled = selected != null
                deleteEndpointButton.isEnabled = selected != null
                displayEndpointDetails(selected)
            }
        }

        // Layout
        val topPanel = JBPanel<JBPanel<*>>(BorderLayout())
        topPanel.add(titleLabel, BorderLayout.NORTH)
        topPanel.add(toolbar, BorderLayout.CENTER)

        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(JBScrollPane(endpointList), BorderLayout.CENTER)

        return panel
    }

    private fun createLogcatPanel(): JComponent {
        return logcatPanel.component
    }

    private fun createEndpointDetailPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 4, 8, 4)

        // Title
        val titleLabel = JBLabel("Endpoint Details")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
        titleLabel.border = JBUI.Borders.emptyBottom(8)

        // Form panel
        val formPanel = JBPanel<JBPanel<*>>()
        formPanel.layout = GridBagLayout()
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(4)
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL

        // Name
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        formPanel.add(JBLabel("Name:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        detailNameField.isEditable = false
        formPanel.add(detailNameField, gbc)

        // Method + Status
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        formPanel.add(JBLabel("Method:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 0.3
        detailMethodLabel.font = detailMethodLabel.font.deriveFont(Font.BOLD)
        formPanel.add(detailMethodLabel, gbc)

        gbc.gridx = 2
        gbc.weightx = 0.0
        formPanel.add(JBLabel("  Status:"), gbc)
        gbc.gridx = 3
        gbc.weightx = 0.2
        detailStatusField.isEditable = false
        formPanel.add(detailStatusField, gbc)

        // Path
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.weightx = 0.0
        gbc.gridwidth = 1
        formPanel.add(JBLabel("Path:"), gbc)
        gbc.gridx = 1
        gbc.gridwidth = 3
        gbc.weightx = 1.0
        detailPathField.isEditable = false
        formPanel.add(detailPathField, gbc)

        // Enabled
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.gridwidth = 1
        gbc.weightx = 0.0
        formPanel.add(JBLabel("Enabled:"), gbc)
        gbc.gridx = 1
        gbc.gridwidth = 3
        detailEnabledLabel.font = detailEnabledLabel.font.deriveFont(Font.BOLD)
        formPanel.add(detailEnabledLabel, gbc)

        // Response Body
        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 4
        gbc.weightx = 1.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL

        val responseLabel = JBLabel("Response Body:")
        responseLabel.border = JBUI.Borders.empty(4, 0, 2, 0)
        formPanel.add(responseLabel, gbc)

        gbc.gridy = 5
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        detailResponseArea.isEditable = false
        detailResponseArea.lineWrap = true
        detailResponseArea.wrapStyleWord = true
        formPanel.add(JBScrollPane(detailResponseArea), gbc)

        panel.add(titleLabel, BorderLayout.NORTH)
        panel.add(formPanel, BorderLayout.CENTER)

        return panel
    }

    private fun createRequestLogPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 4, 8, 8)

        // Title
        val titlePanel = JBPanel<JBPanel<*>>(BorderLayout())
        val titleLabel = JBLabel("Request Log")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
        titlePanel.add(titleLabel, BorderLayout.WEST)

        clearHistoryButton.addActionListener { onClearHistory() }
        clearHistoryButton.toolTipText = "Clear request history"
        titlePanel.add(clearHistoryButton, BorderLayout.EAST)
        titlePanel.border = JBUI.Borders.emptyBottom(8)

        // Table
        requestHistoryTable.fillsViewportHeight = true

        panel.add(titlePanel, BorderLayout.NORTH)
        panel.add(JBScrollPane(requestHistoryTable), BorderLayout.CENTER)

        return panel
    }

    private fun setupListeners() {
        project.messageBus.connect(this).subscribe(
            MockApiChangeListener.TOPIC,
            object : MockApiChangeListener {
                override fun onEndpointCreated(endpoint: MockEndpoint) {
                    SwingUtilities.invokeLater { loadEndpoints() }
                }

                override fun onEndpointUpdated(endpoint: MockEndpoint) {
                    SwingUtilities.invokeLater { loadEndpoints() }
                }

                override fun onEndpointDeleted(endpointId: String) {
                    SwingUtilities.invokeLater { loadEndpoints() }
                }

                override fun onServerStateChanged(running: Boolean, serverUrl: String?) {
                    SwingUtilities.invokeLater { updateServerStatus() }
                }
            }
        )

        // Refresh request history periodically
        Timer(2000) { refreshRequestHistory() }.start()
    }

    private fun loadEndpoints() {
        endpointListModel.clear()
        service.getAllEndpoints().forEach { endpointListModel.addElement(it) }
    }

    /**
     * Create default sample endpoints if none exist
     */
    private fun createDefaultEndpointsIfNeeded() {
        val endpoints = service.getAllEndpoints()
        if (endpoints.isEmpty()) {
            createDefaultEndpoints()
        }
    }

    /**
     * Create two sample endpoints for demonstration
     */
    private fun createDefaultEndpoints() {
        // Sample GET endpoint
        val getEndpoint = MockEndpoint(
            id = "",
            name = "Get Users",
            path = "/api/users",
            method = HttpMethod.GET,
            statusCode = 200,
            responseBody = """
                {
                  "success": true,
                  "data": [
                    {
                      "id": 1,
                      "name": "张三",
                      "email": "zhangsan@example.com",
                      "role": "developer"
                    },
                    {
                      "id": 2,
                      "name": "李四",
                      "email": "lisi@example.com",
                      "role": "designer"
                    },
                    {
                      "id": 3,
                      "name": "王五",
                      "email": "wangwu@example.com",
                      "role": "manager"
                    }
                  ]
                }
            """.trimIndent(),
            headers = mapOf("Content-Type" to "application/json"),
            delay = 0,
            enabled = true,
            description = "获取用户列表 - 示例接口",
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            projectName = project.name
        )

        // Sample POST endpoint
        val postEndpoint = MockEndpoint(
            id = "",
            name = "Create User",
            path = "/api/users",
            method = HttpMethod.POST,
            statusCode = 201,
            responseBody = """
                {
                  "success": true,
                  "message": "用户创建成功",
                  "data": {
                    "id": 4,
                    "name": "新用户",
                    "email": "newuser@example.com",
                    "role": "user",
                    "createdAt": "2024-01-01T12:00:00Z"
                  }
                }
            """.trimIndent(),
            headers = mapOf("Content-Type" to "application/json"),
            delay = 0,
            enabled = true,
            description = "创建新用户 - 示例接口",
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            projectName = project.name
        )

        // Create endpoints
        service.createEndpoint(getEndpoint)
        service.createEndpoint(postEndpoint)

        // Reload the list
        loadEndpoints()

        thisLogger().info("Created default sample endpoints for demonstration")
    }

    private fun displayEndpointDetails(endpoint: MockEndpoint?) {
        if (endpoint == null) {
            detailNameField.text = ""
            detailMethodLabel.text = ""
            detailPathField.text = ""
            detailStatusField.text = ""
            detailResponseArea.text = ""
            detailEnabledLabel.text = ""
            return
        }

        detailNameField.text = endpoint.name
        detailMethodLabel.text = endpoint.method.name
        detailMethodLabel.foreground = getMethodColor(endpoint.method)
        detailPathField.text = endpoint.path
        detailStatusField.text = endpoint.statusCode.toString()
        detailResponseArea.text = endpoint.responseBody
        detailEnabledLabel.text = if (endpoint.enabled) "✓ Yes" else "✗ No"
        detailEnabledLabel.foreground = if (endpoint.enabled) JBColor.GREEN else JBColor.RED
    }

    private fun getMethodColor(method: HttpMethod): Color {
        return when (method) {
            HttpMethod.GET -> JBColor(0x61AFFE, 0x61AFFE)
            HttpMethod.POST -> JBColor(0x49CC90, 0x49CC90)
            HttpMethod.PUT -> JBColor(0xFCA130, 0xFCA130)
            HttpMethod.DELETE -> JBColor(0xF93E3E, 0xF93E3E)
            HttpMethod.PATCH -> JBColor(0x50E3C2, 0x50E3C2)
            else -> JBColor.BLACK
        }
    }

    private fun updateServerStatus() {
        val running = service.isServerRunning()

        if (running) {
            serverStatusLabel.text = "Server: Running"
            serverStatusLabel.foreground = JBColor.GREEN
            startStopButton.text = "Stop Server"
            startStopButton.icon = AllIcons.Actions.Suspend
            copyUrlButton.isEnabled = true
            showAddressesButton.isEnabled = true

            val url = service.getServerUrl() ?: ""
            networkAddressLabel.text = "| $url"
        } else {
            serverStatusLabel.text = "Server: Stopped"
            serverStatusLabel.foreground = JBColor.RED
            startStopButton.text = "Start Server"
            startStopButton.icon = AllIcons.Actions.Execute
            copyUrlButton.isEnabled = false
            showAddressesButton.isEnabled = false
            networkAddressLabel.text = ""
        }
    }

    private fun refreshRequestHistory() {
        SwingUtilities.invokeLater {
            requestHistoryTableModel.setRequests(service.getRequestHistory())
        }
    }

    // ===== Action Handlers =====

    private fun onStartStopServer() {
        if (service.isServerRunning()) {
            val result = service.stopServer()
            result.onFailure { error ->
                Messages.showErrorDialog(project, "Failed to stop server: ${error.message}", "Error")
            }
        } else {
            val result = service.startServer()
            result.onSuccess { url ->
                Messages.showInfoMessage(project, "Mock API server started at $url", "Server Started")
            }.onFailure { error ->
                Messages.showErrorDialog(project, "Failed to start server: ${error.message}", "Error")
            }
        }
    }

    private fun onCopyUrl() {
        // Get selected endpoint
        val selectedEndpoint = endpointList.selectedValue
        if (selectedEndpoint == null) {
            Messages.showWarningDialog(
                project,
                "Please select an endpoint first",
                "No Endpoint Selected"
            )
            return
        }

        // Get network address (prefer LAN address over localhost)
        val addresses = service.getNetworkAddresses()
        val baseUrl = addresses.firstOrNull { !it.contains("localhost") && !it.contains("127.0.0.1") }
            ?: service.getServerUrl()

        if (baseUrl != null) {
            val fullUrl = baseUrl + selectedEndpoint.path
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(fullUrl), null)
            Messages.showInfoMessage(project, "URL copied to clipboard:\n$fullUrl", "Copied")
        } else {
            Messages.showErrorDialog(project, "Server is not running", "Error")
        }
    }

    private fun onShowAddresses() {
        val addresses = service.getNetworkAddresses()
        val dialog = NetworkAddressDialog(project, addresses)
        dialog.show()
    }

    private fun onSettings() {
        val dialog = MockApiSettingsDialog(project)
        dialog.show()
    }

    private fun onAddEndpoint() {
        val dialog = AddEditEndpointDialog(project, null)
        dialog.show()
    }

    private fun onEditEndpoint() {
        val selected = endpointList.selectedValue ?: return
        val dialog = AddEditEndpointDialog(project, selected)
        dialog.show()
    }

    private fun onDeleteEndpoint() {
        val selected = endpointList.selectedValue ?: return

        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete endpoint '${selected.name}'?",
            "Confirm Delete",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            val deleteResult = service.deleteEndpoint(selected.id)
            deleteResult.onFailure { error ->
                Messages.showErrorDialog(project, "Failed to delete endpoint: ${error.message}", "Error")
            }
        }
    }

    private fun onClearHistory() {
        service.clearRequestHistory()
        refreshRequestHistory()
    }

    override fun dispose() {
        thisLogger().debug("MockApiToolWindowContent disposed")
    }

    /**
     * Table model for request history
     */
    private class RequestHistoryTableModel : AbstractTableModel() {
        private val requests = mutableListOf<MockApiRequest>()

        private val columns = arrayOf("Time", "Method", "Path", "Status", "Response Time (ms)")

        fun setRequests(newRequests: List<MockApiRequest>) {
            requests.clear()
            requests.addAll(newRequests)
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = requests.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val request = requests[rowIndex]
            return when (columnIndex) {
                0 -> java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(request.timestamp))
                1 -> request.method
                2 -> request.path
                3 -> request.responseStatus
                4 -> request.responseTime
                else -> ""
            }
        }
    }
}
