package com.quicknote.plugin.mockapi.repository

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.quicknote.plugin.mockapi.model.MockApiConfig
import com.quicknote.plugin.mockapi.model.MockApiConstants
import com.quicknote.plugin.mockapi.model.MockEndpoint
import com.quicknote.plugin.mockapi.model.HttpMethod
import com.quicknote.plugin.settings.QuickNoteSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Repository for Mock API endpoint and configuration persistence
 * Handles file I/O operations for endpoints stored as JSON
 */
@Service(Service.Level.APP)
class MockApiRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        fun getInstance(): MockApiRepository {
            return checkNotNull(
                ApplicationManager.getApplication().getService(MockApiRepository::class.java)
            ) { "MockApiRepository not available" }
        }
    }

    /**
     * Save endpoint to persistent storage
     *
     * @param projectName Project name (used for directory organization)
     * @param endpoint Endpoint to save
     * @return Path to the endpoints file
     */
    @Synchronized
    fun saveEndpoint(projectName: String, endpoint: MockEndpoint): Path {
        val endpoints = findAllEndpoints(projectName).toMutableList()

        // Remove existing endpoint with same ID
        endpoints.removeIf { it.id == endpoint.id }

        // Add new/updated endpoint
        endpoints.add(endpoint)

        return saveAllEndpoints(projectName, endpoints)
    }

    /**
     * Update existing endpoint
     *
     * @param projectName Project name
     * @param endpoint Endpoint to update
     * @return Path to the endpoints file
     */
    fun updateEndpoint(projectName: String, endpoint: MockEndpoint): Path {
        return saveEndpoint(projectName, endpoint)
    }

    /**
     * Delete endpoint from storage
     *
     * @param projectName Project name
     * @param endpointId Endpoint ID
     * @return true if deleted successfully, false otherwise
     */
    @Synchronized
    fun deleteEndpoint(projectName: String, endpointId: String): Boolean {
        val endpoints = findAllEndpoints(projectName).toMutableList()
        val removed = endpoints.removeIf { it.id == endpointId }

        if (removed) {
            saveAllEndpoints(projectName, endpoints)
            thisLogger().debug("Deleted endpoint: $endpointId")
        } else {
            thisLogger().warn("Endpoint not found: $endpointId")
        }

        return removed
    }

    /**
     * Find endpoint by ID
     *
     * @param projectName Project name
     * @param endpointId Endpoint ID
     * @return Endpoint if found, null otherwise
     */
    fun findEndpointById(projectName: String, endpointId: String): MockEndpoint? {
        return findAllEndpoints(projectName).find { it.id == endpointId }
    }

    /**
     * Find all endpoints for a project
     *
     * @param projectName Project name
     * @return List of endpoints, sorted by modification time (newest first)
     */
    fun findAllEndpoints(projectName: String): List<MockEndpoint> {
        val endpointsFile = getEndpointsFilePath(projectName)

        if (!endpointsFile.exists()) {
            thisLogger().debug("Endpoints file not found: $endpointsFile")
            return emptyList()
        }

        return try {
            val content = Files.readString(endpointsFile, StandardCharsets.UTF_8)
            if (content.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString<List<MockEndpoint>>(content)
                    .sortedByDescending { it.modifiedAt }
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to load endpoints from $endpointsFile", e)
            emptyList()
        }
    }

    /**
     * Find endpoint by path and method
     *
     * @param projectName Project name
     * @param method HTTP method
     * @param path API path
     * @return Endpoint if found, null otherwise
     */
    fun findEndpointByPath(projectName: String, method: HttpMethod, path: String): MockEndpoint? {
        return findAllEndpoints(projectName)
            .find { it.method == method && it.path == path }
    }

    /**
     * Save configuration to file
     *
     * @param projectName Project name
     * @param config Configuration to save
     */
    @Synchronized
    fun saveConfig(projectName: String, config: MockApiConfig) {
        val mockApiDir = getMockApiDirectory(projectName)
        Files.createDirectories(mockApiDir)

        val configFile = mockApiDir.resolve(MockApiConstants.CONFIG_FILE)
        val content = json.encodeToString(config)

        Files.writeString(configFile, content, StandardCharsets.UTF_8)
        thisLogger().debug("Saved config to $configFile")
    }

    /**
     * Load configuration from file
     *
     * @param projectName Project name
     * @return Loaded configuration or default if not found
     */
    fun loadConfig(projectName: String): MockApiConfig {
        val configFile = getConfigFilePath(projectName)

        if (!configFile.exists()) {
            thisLogger().debug("Config file not found, using defaults: $configFile")
            return MockApiConfig()
        }

        return try {
            val content = Files.readString(configFile, StandardCharsets.UTF_8)
            if (content.isBlank()) {
                MockApiConfig()
            } else {
                json.decodeFromString<MockApiConfig>(content)
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to load config from $configFile", e)
            MockApiConfig()
        }
    }

    /**
     * Get image storage directory path
     *
     * @param projectName Project name
     * @return Path to image storage directory
     */
    fun getImageStoragePath(projectName: String): Path {
        val config = loadConfig(projectName)

        return if (config.imageStoragePath.isNotBlank()) {
            Paths.get(config.imageStoragePath)
        } else {
            // Default: ~/.quicknote/<project>/mockapi/images/
            getMockApiDirectory(projectName).resolve(MockApiConstants.DEFAULT_IMAGE_DIR)
        }
    }

    /**
     * Resolve image path to full file path
     *
     * @param projectName Project name
     * @param imagePath Relative image path (e.g., "avatar.png")
     * @return Full path to image file, or null if not found
     */
    fun resolveImagePath(projectName: String, imagePath: String): Path? {
        val imageDir = getImageStoragePath(projectName)

        // Remove leading slash if present
        val cleanPath = imagePath.removePrefix("/").removePrefix("images/")
        val fullPath = imageDir.resolve(cleanPath)

        return if (fullPath.exists() && !fullPath.isDirectory()) {
            fullPath
        } else {
            thisLogger().warn("Image file not found: $fullPath")
            null
        }
    }

    /**
     * Save all endpoints to file
     */
    private fun saveAllEndpoints(projectName: String, endpoints: List<MockEndpoint>): Path {
        val mockApiDir = getMockApiDirectory(projectName)
        Files.createDirectories(mockApiDir)

        val endpointsFile = mockApiDir.resolve(MockApiConstants.ENDPOINTS_FILE)
        val content = json.encodeToString(endpoints)

        Files.writeString(endpointsFile, content, StandardCharsets.UTF_8)
        thisLogger().debug("Saved ${endpoints.size} endpoints to $endpointsFile")

        return endpointsFile
    }

    /**
     * Get Mock API storage directory for a project
     */
    private fun getMockApiDirectory(projectName: String): Path {
        val settings = QuickNoteSettings.getInstance()
        val baseDir = settings.storageBasePath
        return Paths.get(baseDir, projectName, MockApiConstants.STORAGE_DIR_NAME)
    }

    /**
     * Get endpoints file path
     */
    private fun getEndpointsFilePath(projectName: String): Path {
        return getMockApiDirectory(projectName).resolve(MockApiConstants.ENDPOINTS_FILE)
    }

    /**
     * Get config file path
     */
    private fun getConfigFilePath(projectName: String): Path {
        return getMockApiDirectory(projectName).resolve(MockApiConstants.CONFIG_FILE)
    }
}
