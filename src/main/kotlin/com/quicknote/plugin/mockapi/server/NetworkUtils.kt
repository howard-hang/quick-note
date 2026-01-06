package com.quicknote.plugin.mockapi.server

import com.intellij.openapi.diagnostic.thisLogger
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket

/**
 * Utility functions for network operations
 */
object NetworkUtils {

    /**
     * Get all local network addresses (IPv4 only)
     *
     * @param port Port number to include in the URL
     * @return List of HTTP URLs for local network interfaces
     */
    fun getLocalNetworkAddresses(port: Int): List<String> {
        return try {
            thisLogger().info("=== Detecting Local Network Addresses ===")
            val allInterfaces = NetworkInterface.getNetworkInterfaces().toList()
            thisLogger().info("Found ${allInterfaces.size} network interface(s)")

            allInterfaces.forEach { netInterface ->
                val displayName = netInterface.displayName ?: "N/A"
                val isUp = netInterface.isUp
                val isLoopback = netInterface.isLoopback
                val addresses = netInterface.inetAddresses.toList()

                thisLogger().info("Interface: ${netInterface.name} ($displayName)")
                thisLogger().info("  - Is Up: $isUp, Is Loopback: $isLoopback")
                thisLogger().info("  - Addresses: ${addresses.size}")
                addresses.forEach { addr ->
                    val isIPv4 = addr is Inet4Address
                    val isLoopbackAddr = addr.isLoopbackAddress
                    thisLogger().info("    - ${addr.hostAddress} (IPv4: $isIPv4, Loopback: $isLoopbackAddr)")
                }
            }

            val filteredInterfaces = allInterfaces.filter { it.isUp && !it.isLoopback }
            thisLogger().info("After filtering (isUp && !isLoopback): ${filteredInterfaces.size} interface(s)")

            val result = filteredInterfaces
                .flatMap { networkInterface ->
                    networkInterface.inetAddresses.toList()
                        .filter { it is Inet4Address && !it.isLoopbackAddress }
                        .map { "http://${it.hostAddress}:$port" }
                }
                .distinct()

            thisLogger().info("Final network addresses: ${result.size}")
            result.forEach { addr ->
                thisLogger().info("  - $addr")
            }

            result
        } catch (e: Exception) {
            thisLogger().error("Failed to get local network addresses", e)
            emptyList()
        }
    }

    /**
     * Get the local IP address (first non-loopback IPv4 address)
     *
     * @return Local IP address string, or null if not found
     */
    fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        } catch (e: Exception) {
            thisLogger().error("Failed to get local IP address", e)
            null
        }
    }

    /**
     * Get localhost address with port
     *
     * @param port Port number
     * @return Localhost URL
     */
    fun getLocalhostAddress(port: Int): String {
        return "http://localhost:$port"
    }

    /**
     * Check if a port is available for binding
     *
     * @param port Port number to check
     * @return true if port is available, false otherwise
     */
    fun isPortAvailable(port: Int): Boolean {
        if (port < 1 || port > 65535) {
            thisLogger().warn("Invalid port number: $port (must be 1-65535)")
            return false
        }

        return try {
            thisLogger().debug("Testing port $port availability...")
            ServerSocket(port).use {
                it.reuseAddress = true
                thisLogger().debug("Port $port is AVAILABLE")
                true
            }
        } catch (e: Exception) {
            thisLogger().warn("Port $port is NOT available: ${e.message}")
            thisLogger().warn("Exception type: ${e::class.java.name}")
            false
        }
    }

    /**
     * Find an available port starting from the given port
     *
     * @param startPort Starting port number
     * @param maxAttempts Maximum number of ports to try
     * @return Available port number, or null if none found
     */
    fun findAvailablePort(startPort: Int, maxAttempts: Int = 10): Int? {
        for (i in 0 until maxAttempts) {
            val port = startPort + i
            if (isPortAvailable(port)) {
                return port
            }
        }
        return null
    }

    /**
     * Get all network addresses including localhost
     *
     * @param port Port number
     * @return List of all accessible URLs
     */
    fun getAllAccessibleAddresses(port: Int): List<String> {
        val addresses = mutableListOf(getLocalhostAddress(port))
        addresses.addAll(getLocalNetworkAddresses(port))
        return addresses.distinct()
    }
}
