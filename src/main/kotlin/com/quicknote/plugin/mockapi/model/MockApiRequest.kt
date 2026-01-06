package com.quicknote.plugin.mockapi.model

/**
 * Represents a logged HTTP request to the Mock API server
 *
 * @property id Request identifier
 * @property endpointId Related endpoint ID (if matched)
 * @property timestamp Request timestamp
 * @property method HTTP method
 * @property path Request path
 * @property queryParams Query parameters
 * @property headers Request headers
 * @property body Request body
 * @property responseStatus Response HTTP status code
 * @property responseTime Response time in milliseconds
 */
data class MockApiRequest(
    val id: String,
    val endpointId: String?,
    val timestamp: Long,
    val method: String,
    val path: String,
    val queryParams: Map<String, List<String>>,
    val headers: Map<String, List<String>>,
    val body: String?,
    val responseStatus: Int,
    val responseTime: Long
)
