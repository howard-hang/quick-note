package com.quicknote.plugin.mockapi.model

import kotlinx.serialization.Serializable

/**
 * Represents a mock API endpoint configuration
 *
 * @property id Unique identifier (UUID)
 * @property name Display name for the endpoint
 * @property path API path (e.g., "/api/users")
 * @property method HTTP method
 * @property statusCode HTTP status code to return
 * @property responseBody JSON response body
 * @property headers Custom response headers
 * @property delay Response delay in milliseconds
 * @property enabled Whether the endpoint is enabled
 * @property description Endpoint description
 * @property createdAt Creation timestamp
 * @property modifiedAt Last modification timestamp
 * @property projectName Associated project name
 */
@Serializable
data class MockEndpoint(
    val id: String,
    val name: String,
    val path: String,
    val method: HttpMethod,
    val statusCode: Int = 200,
    val responseBody: String,
    val headers: Map<String, String> = emptyMap(),
    val delay: Long = 0,
    val enabled: Boolean = true,
    val description: String = "",
    val createdAt: Long,
    val modifiedAt: Long,
    val projectName: String
)
