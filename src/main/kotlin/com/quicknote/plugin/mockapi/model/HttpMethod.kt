package com.quicknote.plugin.mockapi.model

import kotlinx.serialization.Serializable

/**
 * HTTP methods supported by the Mock API server
 */
@Serializable
enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    OPTIONS,
    HEAD
}
