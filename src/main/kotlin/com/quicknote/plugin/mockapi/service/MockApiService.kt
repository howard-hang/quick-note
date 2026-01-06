package com.quicknote.plugin.mockapi.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.quicknote.plugin.mockapi.model.MockApiConfig
import com.quicknote.plugin.mockapi.model.MockApiConstants
import com.quicknote.plugin.mockapi.model.MockApiRequest
import com.quicknote.plugin.mockapi.model.MockEndpoint
import com.quicknote.plugin.mockapi.repository.MockApiRepository
import com.quicknote.plugin.mockapi.server.EmbeddedHttpServer
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Project-level service for Mock API operations
 * Coordinates between UI, repository, and HTTP server
 */
@Service(Service.Level.PROJECT)
class MockApiService(private val project: Project) : EmbeddedHttpServer.RequestListener {

    private val repository = MockApiRepository.getInstance()
    private val server = EmbeddedHttpServer.getInstance()
    private val requestHistory = ConcurrentLinkedQueue<MockApiRequest>()

    init {
        server.addRequestListener(this)
    }

    companion object {
        fun getInstance(project: Project): MockApiService {
            return checkNotNull(project.getService(MockApiService::class.java)) {
                "MockApiService not available"
            }
        }
    }

    // ===== Endpoint Management =====

    /**
     * Create a new endpoint
     *
     * @param endpoint Endpoint to create (ID will be generated if empty)
     * @return Result with the created endpoint or error
     */
    fun createEndpoint(endpoint: MockEndpoint): Result<MockEndpoint> {
        return try {
            val endpointWithId = if (endpoint.id.isBlank()) {
                endpoint.copy(
                    id = UUID.randomUUID().toString(),
                    projectName = project.name,
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis()
                )
            } else {
                endpoint.copy(
                    projectName = project.name,
                    modifiedAt = System.currentTimeMillis()
                )
            }

            repository.saveEndpoint(project.name, endpointWithId)
            publishEndpointCreated(endpointWithId)

            // Restart server if running to apply new endpoint
            if (server.isRunning()) {
                restartServerInternal()
            }

            thisLogger().info("Endpoint created: ${endpointWithId.id}")
            Result.success(endpointWithId)
        } catch (e: Exception) {
            thisLogger().error("Failed to create endpoint", e)
            Result.failure(e)
        }
    }

    /**
     * Update an existing endpoint
     *
     * @param endpoint Endpoint to update
     * @return Result with the updated endpoint or error
     */
    fun updateEndpoint(endpoint: MockEndpoint): Result<MockEndpoint> {
        return try {
            val updatedEndpoint = endpoint.copy(
                projectName = project.name,
                modifiedAt = System.currentTimeMillis()
            )

            repository.updateEndpoint(project.name, updatedEndpoint)
            publishEndpointUpdated(updatedEndpoint)

            // Restart server if running to apply changes
            if (server.isRunning()) {
                restartServerInternal()
            }

            thisLogger().info("Endpoint updated: ${updatedEndpoint.id}")
            Result.success(updatedEndpoint)
        } catch (e: Exception) {
            thisLogger().error("Failed to update endpoint: ${endpoint.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete an endpoint
     *
     * @param endpointId ID of the endpoint to delete
     * @return Result with success or error
     */
    fun deleteEndpoint(endpointId: String): Result<Unit> {
        return try {
            val deleted = repository.deleteEndpoint(project.name, endpointId)

            if (deleted) {
                publishEndpointDeleted(endpointId)

                // Restart server if running to remove endpoint
                if (server.isRunning()) {
                    restartServerInternal()
                }

                thisLogger().info("Endpoint deleted: $endpointId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Endpoint not found: $endpointId"))
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to delete endpoint: $endpointId", e)
            Result.failure(e)
        }
    }

    /**
     * Get an endpoint by ID
     *
     * @param endpointId Endpoint ID
     * @return Endpoint if found, null otherwise
     */
    fun getEndpoint(endpointId: String): MockEndpoint? {
        return repository.findEndpointById(project.name, endpointId)
    }

    /**
     * Get all endpoints for the current project
     *
     * @return List of endpoints, sorted by modification time (newest first)
     */
    fun getAllEndpoints(): List<MockEndpoint> {
        return repository.findAllEndpoints(project.name)
    }

    /**
     * Toggle endpoint enabled/disabled state
     *
     * @param endpointId Endpoint ID
     * @param enabled New enabled state
     * @return Result with the updated endpoint or error
     */
    fun toggleEndpoint(endpointId: String, enabled: Boolean): Result<MockEndpoint> {
        return try {
            val endpoint = repository.findEndpointById(project.name, endpointId)
                ?: return Result.failure(Exception("Endpoint not found: $endpointId"))

            val updated = endpoint.copy(enabled = enabled, modifiedAt = System.currentTimeMillis())
            repository.updateEndpoint(project.name, updated)
            publishEndpointUpdated(updated)

            // Restart server if running to apply changes
            if (server.isRunning()) {
                restartServerInternal()
            }

            thisLogger().info("Endpoint ${if (enabled) "enabled" else "disabled"}: $endpointId")
            Result.success(updated)
        } catch (e: Exception) {
            thisLogger().error("Failed to toggle endpoint: $endpointId", e)
            Result.failure(e)
        }
    }

    // ===== Server Control =====

    /**
     * Start the Mock API server
     *
     * @return Result with server URL or error
     */
    fun startServer(): Result<String> {
        return try {
            thisLogger().info("========================================")
            thisLogger().info("MockApiService.startServer() called")
            thisLogger().info("Project: ${project.name}")
            thisLogger().info("========================================")

            if (server.isRunning()) {
                thisLogger().warn("Server is already running, cannot start")
                return Result.failure(IllegalStateException("Server is already running"))
            }

            thisLogger().info("Loading configuration for project: ${project.name}")
            val config = repository.loadConfig(project.name)
            thisLogger().info("Configuration loaded: host=${config.host}, port=${config.port}")

            thisLogger().info("Calling EmbeddedHttpServer.start()...")
            server.start(project.name, config)

            val url = server.getServerUrl()
            thisLogger().info("Server URL: $url")
            if (url == null) {
                thisLogger().error("Failed to get server URL (null returned)")
                return Result.failure(Exception("Failed to get server URL"))
            }

            val networkAddresses = server.getNetworkAddresses()
            thisLogger().info("Network addresses available: ${networkAddresses.size}")
            networkAddresses.forEach { addr ->
                thisLogger().info("  - $addr")
            }

            publishServerStateChanged(true, url)
            thisLogger().info("✓ Mock API server started successfully: $url")
            thisLogger().info("========================================")
            Result.success(url)
        } catch (e: Exception) {
            thisLogger().error("!!! Failed to start Mock API server !!!", e)
            thisLogger().error("Exception type: ${e::class.java.name}")
            thisLogger().error("Exception message: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Stop the Mock API server
     *
     * @return Result with success or error
     */
    fun stopServer(): Result<Unit> {
        return try {
            if (!server.isRunning()) {
                return Result.failure(IllegalStateException("Server is not running"))
            }

            server.stop()
            publishServerStateChanged(false, null)

            thisLogger().info("Mock API server stopped")
            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().error("Failed to stop Mock API server", e)
            Result.failure(e)
        }
    }

    /**
     * Restart the Mock API server
     *
     * @return Result with server URL or error
     */
    fun restartServer(): Result<String> {
        return try {
            if (server.isRunning()) {
                server.stop()
            }

            val config = repository.loadConfig(project.name)
            server.start(project.name, config)

            val url = server.getServerUrl()
                ?: return Result.failure(Exception("Failed to get server URL"))

            publishServerStateChanged(true, url)
            thisLogger().info("Mock API server restarted: $url")
            Result.success(url)
        } catch (e: Exception) {
            thisLogger().error("Failed to restart Mock API server", e)
            Result.failure(e)
        }
    }

    /**
     * Check if server is running
     *
     * @return true if server is running, false otherwise
     */
    fun isServerRunning(): Boolean {
        return server.isRunning()
    }

    /**
     * Get server URL (localhost)
     *
     * @return Server URL or null if not running
     */
    fun getServerUrl(): String? {
        return server.getServerUrl()
    }

    /**
     * Get all network addresses where the server is accessible
     *
     * @return List of HTTP URLs
     */
    fun getNetworkAddresses(): List<String> {
        return server.getNetworkAddresses()
    }

    // ===== Configuration Management =====

    /**
     * Update server configuration
     *
     * @param config New configuration
     * @return Result with success or error
     */
    fun updateConfig(config: MockApiConfig): Result<Unit> {
        return try {
            repository.saveConfig(project.name, config)

            // Restart server if running to apply new config
            if (server.isRunning()) {
                restartServerInternal()
            }

            thisLogger().info("Mock API configuration updated")
            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().error("Failed to update configuration", e)
            Result.failure(e)
        }
    }

    /**
     * Get current server configuration
     *
     * @return Server configuration
     */
    fun getConfig(): MockApiConfig {
        return repository.loadConfig(project.name)
    }

    /**
     * Get image storage path
     *
     * @return Image storage path
     */
    fun getImageStoragePath(): String {
        return repository.getImageStoragePath(project.name).toString()
    }

    /**
     * Set image storage path
     *
     * @param path New image storage path
     * @return Result with success or error
     */
    fun setImageStoragePath(path: String): Result<Unit> {
        return try {
            val config = getConfig()
            val updatedConfig = config.copy(imageStoragePath = path)
            repository.saveConfig(project.name, updatedConfig)

            thisLogger().info("Image storage path updated: $path")
            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().error("Failed to set image storage path", e)
            Result.failure(e)
        }
    }

    /**
     * Validate image path exists
     *
     * @param path Path to validate
     * @return true if path exists and is a directory, false otherwise
     */
    fun validateImagePath(path: String): Boolean {
        return try {
            val p = Paths.get(path)
            p.toFile().exists() && p.toFile().isDirectory
        } catch (e: Exception) {
            false
        }
    }

    // ===== Request History =====

    /**
     * Get request history
     *
     * @return List of requests, newest first
     */
    fun getRequestHistory(): List<MockApiRequest> {
        return requestHistory.toList().sortedByDescending { it.timestamp }
    }

    /**
     * Clear request history
     */
    fun clearRequestHistory() {
        requestHistory.clear()
        thisLogger().debug("Request history cleared")
    }

    /**
     * Request listener callback
     */
    override fun onRequest(request: MockApiRequest) {
        // Add to history
        requestHistory.add(request)

        // Limit history size
        while (requestHistory.size > MockApiConstants.MAX_REQUEST_HISTORY) {
            requestHistory.poll()
        }
    }

    // ===== Event Publishing =====

    private fun publishEndpointCreated(endpoint: MockEndpoint) {
        project.messageBus
            .syncPublisher(MockApiChangeListener.TOPIC)
            .onEndpointCreated(endpoint)
    }

    private fun publishEndpointUpdated(endpoint: MockEndpoint) {
        project.messageBus
            .syncPublisher(MockApiChangeListener.TOPIC)
            .onEndpointUpdated(endpoint)
    }

    private fun publishEndpointDeleted(endpointId: String) {
        project.messageBus
            .syncPublisher(MockApiChangeListener.TOPIC)
            .onEndpointDeleted(endpointId)
    }

    private fun publishServerStateChanged(running: Boolean, serverUrl: String?) {
        project.messageBus
            .syncPublisher(MockApiChangeListener.TOPIC)
            .onServerStateChanged(running, serverUrl)
    }

    /**
     * Internal restart without publishing events (to avoid loops)
     */
    private fun restartServerInternal() {
        try {
            val config = repository.loadConfig(project.name)
            server.stop()
            server.start(project.name, config)
            thisLogger().debug("Server restarted internally")
        } catch (e: Exception) {
            thisLogger().error("Failed to restart server internally", e)
        }
    }
}
