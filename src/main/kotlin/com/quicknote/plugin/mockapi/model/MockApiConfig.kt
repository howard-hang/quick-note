package com.quicknote.plugin.mockapi.model

import kotlinx.serialization.Serializable

/**
 * Configuration for the Mock API server
 *
 * @property port Server port
 * @property host Server host (0.0.0.0 for all interfaces)
 * @property imageStoragePath Custom image storage directory path
 * @property enableCors Enable CORS support
 * @property enableLogging Enable request logging
 */
@Serializable
data class MockApiConfig(
    val port: Int = MockApiConstants.DEFAULT_PORT,
    val host: String = MockApiConstants.DEFAULT_HOST,
    val imageStoragePath: String = "",
    val enableCors: Boolean = true,
    val enableLogging: Boolean = true
)
