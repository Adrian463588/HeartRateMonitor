package com.example.samplewearmobileapp.data.repository

import com.example.samplewearmobileapp.models.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for Wearable Data Layer communication between mobile and wear modules.
 *
 * Replaces the deprecated [com.google.android.gms.common.api.GoogleApiClient] pattern with
 * the modern [com.google.android.gms.wearable.MessageClient] and
 * [com.google.android.gms.wearable.DataClient] Task-based APIs (.await() coroutines).
 *
 * All methods are non-blocking; background dispatching is managed internally.
 */
interface WearableRepository {

    /**
     * Emits the list of currently connected Wear OS node IDs.
     * An empty list means no paired/connected watch is available.
     */
    val connectedNodeIds: StateFlow<List<String>>

    /**
     * Emits incoming [Message] objects received from the watch.
     * Hot flow — subscribers must be active to receive events.
     */
    val incomingMessages: Flow<Message>

    /**
     * Sends a [Message] to all currently connected nodes at the given Wearable Data Layer path.
     *
     * @param message The serialised [Message] to send.
     * @param path    The Wearable Data Layer path (e.g. [MessagePath.COMMAND]).
     * @return `true` if at least one node received the message, `false` otherwise.
     */
    suspend fun sendMessage(message: Message, path: String): Boolean

    /**
     * Pushes a raw [ByteArray] payload to the DataClient at the given [path].
     *
     * Useful for bulk sensor data that exceeds the MessageClient 100KB limit.
     *
     * @param data Raw bytes to transmit.
     * @param path DataItem path.
     */
    suspend fun sendData(data: ByteArray, path: String)

    /**
     * Refreshes the list of connected nodes.
     * Called automatically on creation; can be called manually after a reconnect.
     */
    suspend fun refreshConnectedNodes()

    /** Unregisters all listeners. Call from [MainViewModel.onCleared]. */
    fun destroy()
}
