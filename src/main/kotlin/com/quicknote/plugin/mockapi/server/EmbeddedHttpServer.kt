package com.quicknote.plugin.mockapi.server

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.quicknote.plugin.mockapi.model.HttpMethod
import com.quicknote.plugin.mockapi.model.MockApiConfig
import com.quicknote.plugin.mockapi.model.MockApiRequest
import com.quicknote.plugin.mockapi.model.MockEndpoint
import com.quicknote.plugin.mockapi.repository.MockApiRepository
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Embedded HTTP server for serving mock API endpoints.
 * Uses JDK HttpServer to avoid IDE runtime conflicts.
 */
@Service(Service.Level.APP)
class EmbeddedHttpServer {

    private var server: HttpServer? = null
    private var executor: ExecutorService? = null
    private var currentProjectName: String? = null
    private var currentConfig: MockApiConfig? = null
    private val requestListeners = CopyOnWriteArrayList<RequestListener>()
    private val threadCounter = AtomicInteger(0)

    companion object {
        fun getInstance(): EmbeddedHttpServer {
            return checkNotNull(
                ApplicationManager.getApplication().getService(EmbeddedHttpServer::class.java)
            ) { "EmbeddedHttpServer not available" }
        }
    }

    /**
     * Start the HTTP server.
     *
     * @param projectName Project name to load endpoints from
     * @param config Server configuration
     * @throws IllegalStateException if server is already running
     * @throws Exception if server fails to start
     */
    fun start(projectName: String, config: MockApiConfig) {
        thisLogger().info("=== Mock API Server Start Request ===")
        thisLogger().info("Project: $projectName")
        thisLogger().info("Config - Host: ${config.host}, Port: ${config.port}")
        thisLogger().info("Config - CORS: ${config.enableCors}, Logging: ${config.enableLogging}")

        if (isRunning()) {
            thisLogger().warn("Server start failed: Server is already running")
            throw IllegalStateException("Server is already running. Stop it first.")
        }
        thisLogger().info("Server status check: Not running, proceeding with start")

        thisLogger().info("Checking if port ${config.port} is available...")
        if (!NetworkUtils.isPortAvailable(config.port)) {
            thisLogger().error("Port ${config.port} is NOT available (already in use)")
            throw IllegalStateException(
                "Port ${config.port} is already in use. " +
                    "Please choose a different port or stop the application using it."
            )
        }
        thisLogger().info("Port ${config.port} is available")

        currentProjectName = projectName
        currentConfig = config

        try {
            thisLogger().info("Creating embedded JDK HttpServer...")
            val httpServer = HttpServer.create(InetSocketAddress(config.host, config.port), 0)
            val serverExecutor = createExecutor()
            httpServer.executor = serverExecutor

            httpServer.createContext("/images") { exchange ->
                handleImageRequest(exchange, projectName, config)
            }
            httpServer.createContext("/") { exchange ->
                handleApiRequest(exchange, projectName, config)
            }

            httpServer.start()
            server = httpServer
            executor = serverExecutor

            thisLogger().info("=== Mock API Server Started Successfully ===")
            thisLogger().info("Listening on: ${config.host}:${config.port}")

            val addresses = NetworkUtils.getAllAccessibleAddresses(config.port)
            thisLogger().info("Server is accessible at ${addresses.size} address(es):")
            addresses.forEach { addr ->
                thisLogger().info("  - $addr")
            }

            val repository = MockApiRepository.getInstance()
            val endpoints = repository.findAllEndpoints(projectName)
            thisLogger().info("Loaded ${endpoints.size} endpoint(s) for project '$projectName'")
            thisLogger().info("Enabled endpoints: ${endpoints.count { it.enabled }}")
            endpoints.filter { it.enabled }.forEach { endpoint ->
                thisLogger().info("  - ${endpoint.method} ${endpoint.path}")
            }
        } catch (e: Exception) {
            server = null
            currentProjectName = null
            currentConfig = null
            executor?.shutdownNow()
            executor = null
            thisLogger().error("Failed to start Mock API server", e)
            throw e
        }
    }

    /**
     * Stop the HTTP server.
     */
    fun stop() {
        try {
            server?.stop(1)
            thisLogger().info("Mock API server stopped")
        } catch (e: Exception) {
            thisLogger().error("Error stopping Mock API server", e)
        } finally {
            server = null
            currentProjectName = null
            currentConfig = null
            executor?.shutdownNow()
            executor = null
        }
    }

    /**
     * Check if server is running.
     */
    fun isRunning(): Boolean {
        return server != null
    }

    /**
     * Get server URL (localhost).
     */
    fun getServerUrl(): String? {
        return currentConfig?.let { config ->
            NetworkUtils.getLocalhostAddress(config.port)
        }
    }

    /**
     * Get all network addresses where the server is accessible.
     */
    fun getNetworkAddresses(): List<String> {
        return currentConfig?.let { config ->
            NetworkUtils.getAllAccessibleAddresses(config.port)
        } ?: emptyList()
    }

    /**
     * Add a request listener.
     */
    fun addRequestListener(listener: RequestListener) {
        requestListeners.add(listener)
    }

    /**
     * Remove a request listener.
     */
    fun removeRequestListener(listener: RequestListener) {
        requestListeners.remove(listener)
    }

    private fun createExecutor(): ExecutorService {
        val factory = ThreadFactory { runnable ->
            Thread(runnable, "QuickNoteMockApi-" + threadCounter.incrementAndGet()).apply {
                isDaemon = true
            }
        }
        return Executors.newCachedThreadPool(factory)
    }

    private fun handleImageRequest(exchange: HttpExchange, projectName: String, config: MockApiConfig) {
        try {
            val method = exchange.requestMethod
            if (config.enableCors && method.equals("OPTIONS", ignoreCase = true)) {
                sendOptionsResponse(exchange, config)
                return
            }
            if (!method.equals("GET", ignoreCase = true)) {
                sendTextResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=UTF-8", config)
                return
            }

            val requestPath = exchange.requestURI.path ?: ""
            val filename = when {
                requestPath == "/images" || requestPath == "/images/" -> ""
                requestPath.startsWith("/images/") -> requestPath.removePrefix("/images/")
                else -> ""
            }

            if (filename.isBlank()) {
                sendTextResponse(exchange, 400, "Filename is required", "text/plain; charset=UTF-8", config)
                return
            }

            val repository = MockApiRepository.getInstance()
            val imagePath = repository.resolveImagePath(projectName, filename)

            if (imagePath != null && Files.exists(imagePath)) {
                val contentType = Files.probeContentType(imagePath) ?: "application/octet-stream"
                val bytes = Files.readAllBytes(imagePath)
                sendBytesResponse(exchange, 200, bytes, contentType, config)
            } else {
                sendTextResponse(exchange, 404, "Image not found: $filename", "text/plain; charset=UTF-8", config)
            }
        } catch (e: Exception) {
            thisLogger().error("Error serving image", e)
            safeSendError(exchange, config, e)
        } finally {
            exchange.close()
        }
    }

    private fun handleApiRequest(exchange: HttpExchange, projectName: String, config: MockApiConfig) {
        val startTime = System.currentTimeMillis()
        val requestPath = exchange.requestURI.path ?: "/"
        val requestMethod = exchange.requestMethod ?: "GET"
        val queryParams = parseQueryParams(exchange.requestURI.rawQuery)
        val requestHeaders = exchange.requestHeaders.mapValues { it.value.toList() }
        val requestBody = if (config.enableLogging) readRequestBody(exchange) else null

        try {
            thisLogger().info("=== Incoming HTTP Request ===")
            thisLogger().info("Method: $requestMethod, Path: $requestPath")
            thisLogger().info("Remote Address: ${exchange.remoteAddress.address?.hostAddress}")

            if (config.enableCors && requestMethod.equals("OPTIONS", ignoreCase = true)) {
                sendOptionsResponse(exchange, config)
                return
            }

            val repository = MockApiRepository.getInstance()
            val allEndpoints = repository.findAllEndpoints(projectName)

            val endpoint = allEndpoints.find { candidate ->
                candidate.enabled && candidate.path == requestPath && matchHttpMethod(candidate.method, requestMethod)
            }

            if (endpoint != null) {
                if (endpoint.delay > 0) {
                    Thread.sleep(endpoint.delay)
                }

                val customContentType = applyResponseHeaders(exchange.responseHeaders, endpoint)
                val contentType = ensureCharset(customContentType ?: "application/json")
                val responseBody = endpoint.responseBody

                sendTextResponse(exchange, endpoint.statusCode, responseBody, contentType, config)

                val responseTime = System.currentTimeMillis() - startTime
                if (config.enableLogging) {
                    logRequest(endpoint, requestMethod, requestPath, queryParams, requestHeaders, requestBody, endpoint.statusCode, responseTime)
                }
            } else {
                val body = buildNotFoundBody(requestMethod, requestPath)
                sendTextResponse(exchange, 404, body, "application/json; charset=UTF-8", config)

                val responseTime = System.currentTimeMillis() - startTime
                if (config.enableLogging) {
                    logRequest(null, requestMethod, requestPath, queryParams, requestHeaders, requestBody, 404, responseTime)
                }
            }
        } catch (e: Exception) {
            thisLogger().error("Error handling request", e)
            safeSendError(exchange, config, e)
        } finally {
            exchange.close()
        }
    }

    private fun applyResponseHeaders(headers: Headers, endpoint: MockEndpoint): String? {
        var contentType: String? = null
        endpoint.headers.forEach { (key, value) ->
            if (key.equals("Content-Type", ignoreCase = true)) {
                contentType = value
            } else {
                headers.add(key, value)
            }
        }
        return contentType
    }

    private fun sendOptionsResponse(exchange: HttpExchange, config: MockApiConfig) {
        if (config.enableCors) {
            addCorsHeaders(exchange.responseHeaders)
        }
        exchange.sendResponseHeaders(204, 0)
    }

    private fun sendTextResponse(
        exchange: HttpExchange,
        statusCode: Int,
        body: String,
        contentType: String,
        config: MockApiConfig
    ) {
        val bytes = if (body.isNotEmpty()) body.toByteArray(StandardCharsets.UTF_8) else ByteArray(0)
        sendBytesResponse(exchange, statusCode, bytes, contentType, config)
    }

    private fun sendBytesResponse(
        exchange: HttpExchange,
        statusCode: Int,
        bytes: ByteArray,
        contentType: String,
        config: MockApiConfig
    ) {
        if (config.enableCors) {
            addCorsHeaders(exchange.responseHeaders)
        }
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { output ->
            if (bytes.isNotEmpty()) {
                output.write(bytes)
            }
        }
    }

    private fun addCorsHeaders(headers: Headers) {
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization")
        headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,PATCH,HEAD,OPTIONS")
        headers.set("Access-Control-Max-Age", "86400")
    }

    private fun ensureCharset(contentType: String): String {
        return if (contentType.lowercase(Locale.US).contains("charset=")) {
            contentType
        } else {
            "$contentType; charset=UTF-8"
        }
    }

    private fun buildNotFoundBody(method: String, path: String): String {
        return "{\"error\":\"No mock endpoint found\",\"method\":\"${escapeJson(method)}\",\"path\":\"${escapeJson(path)}\"}"
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length + 16)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') {
                    sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, List<String>> {
        if (rawQuery.isNullOrBlank()) {
            return emptyMap()
        }

        val params = linkedMapOf<String, MutableList<String>>()
        rawQuery.split('&').forEach { pair ->
            if (pair.isBlank()) {
                return@forEach
            }
            val parts = pair.split('=', limit = 2)
            val key = urlDecode(parts[0])
            val value = if (parts.size > 1) urlDecode(parts[1]) else ""
            params.getOrPut(key) { mutableListOf() }.add(value)
        }

        return params.mapValues { it.value.toList() }
    }

    private fun urlDecode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }

    private fun readRequestBody(exchange: HttpExchange): String? {
        val lengthHeader = exchange.requestHeaders.getFirst("Content-Length")?.toLongOrNull()
        if (lengthHeader == null || lengthHeader <= 0) {
            return null
        }
        return try {
            val bytes = exchange.requestBody.readNBytes(lengthHeader.toInt())
            if (bytes.isEmpty()) null else String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun matchHttpMethod(endpointMethod: HttpMethod, requestMethod: String): Boolean {
        return endpointMethod.name.equals(requestMethod, ignoreCase = true)
    }

    private fun logRequest(
        endpoint: MockEndpoint?,
        method: String,
        path: String,
        queryParams: Map<String, List<String>>,
        headers: Map<String, List<String>>,
        body: String?,
        statusCode: Int,
        responseTime: Long
    ) {
        try {
            val request = MockApiRequest(
                id = UUID.randomUUID().toString(),
                endpointId = endpoint?.id,
                timestamp = System.currentTimeMillis(),
                method = method,
                path = path,
                queryParams = queryParams,
                headers = headers,
                body = body,
                responseStatus = statusCode,
                responseTime = responseTime
            )

            requestListeners.forEach { listener ->
                try {
                    listener.onRequest(request)
                } catch (e: Exception) {
                    thisLogger().warn("Request listener failed", e)
                }
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to log request", e)
        }
    }

    private fun safeSendError(exchange: HttpExchange, config: MockApiConfig, error: Exception) {
        try {
            sendTextResponse(
                exchange,
                500,
                "Internal Server Error: ${error.message}",
                "text/plain; charset=UTF-8",
                config
            )
        } catch (e: Exception) {
            thisLogger().error("Failed to send error response", e)
        }
    }

    /**
     * Listener interface for request logging.
     */
    interface RequestListener {
        fun onRequest(request: MockApiRequest)
    }
}
