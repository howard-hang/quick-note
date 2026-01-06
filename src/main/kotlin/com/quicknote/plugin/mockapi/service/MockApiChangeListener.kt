package com.quicknote.plugin.mockapi.service

import com.intellij.util.messages.Topic
import com.quicknote.plugin.mockapi.model.MockEndpoint

/**
 * Listener interface for Mock API changes
 * Uses IntelliJ's message bus for event publishing
 */
interface MockApiChangeListener {

    /**
     * Called when an endpoint is created
     *
     * @param endpoint The created endpoint
     */
    fun onEndpointCreated(endpoint: MockEndpoint) {}

    /**
     * Called when an endpoint is updated
     *
     * @param endpoint The updated endpoint
     */
    fun onEndpointUpdated(endpoint: MockEndpoint) {}

    /**
     * Called when an endpoint is deleted
     *
     * @param endpointId ID of the deleted endpoint
     */
    fun onEndpointDeleted(endpointId: String) {}

    /**
     * Called when the server state changes (started/stopped)
     *
     * @param running true if server is now running, false if stopped
     * @param serverUrl Server URL if running, null if stopped
     */
    fun onServerStateChanged(running: Boolean, serverUrl: String? = null) {}

    companion object {
        val TOPIC = Topic.create(
            "MockApiChanges",
            MockApiChangeListener::class.java
        )
    }
}
