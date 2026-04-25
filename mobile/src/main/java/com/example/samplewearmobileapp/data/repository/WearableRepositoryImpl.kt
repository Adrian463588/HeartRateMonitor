package com.example.samplewearmobileapp.data.repository

import android.content.Context
import android.util.Log
import com.example.samplewearmobileapp.constants.MessagePath
import com.example.samplewearmobileapp.models.Message
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Production implementation of [WearableRepository] using the modern Wearable Data Layer API.
 *
 * Replaces the deprecated [com.google.android.gms.common.api.GoogleApiClient] pattern
 * with [MessageClient], [DataClient], and [NodeClient] accessed via `.await()` coroutines.
 *
 * **Architecture:**
 * - Message receipt is handled by a [MessageClient.OnMessageReceivedListener] that bridges
 *   into a [MutableSharedFlow], decoupling the SDK callback from Flow collectors.
 * - All send operations run on [Dispatchers.IO] via the internal scope.
 *
 * @param context Application context to obtain Wearable clients.
 */
class WearableRepositoryImpl(context: Context) : WearableRepository,
    MessageClient.OnMessageReceivedListener {

    private val tag = "WearableRepository"
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val messageClient: MessageClient = Wearable.getMessageClient(appContext)
    private val dataClient: DataClient = Wearable.getDataClient(appContext)
    private val nodeClient: NodeClient = Wearable.getNodeClient(appContext)

    private val _connectedNodeIds = MutableStateFlow<List<String>>(emptyList())
    override val connectedNodeIds: StateFlow<List<String>> = _connectedNodeIds.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>(extraBufferCapacity = 16)
    override val incomingMessages: Flow<Message> = _incomingMessages.asSharedFlow()

    init {
        messageClient.addListener(this)
        scope.launch { refreshConnectedNodes() }
    }

    // -------------------------------------------------------------------------
    // MessageClient.OnMessageReceivedListener
    // -------------------------------------------------------------------------

    override fun onMessageReceived(messageEvent: MessageEvent) {
        try {
            val json = String(messageEvent.data)
            val message = gson.fromJson(json, Message::class.java)
            scope.launch { _incomingMessages.emit(message) }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse incoming message: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // WearableRepository interface
    // -------------------------------------------------------------------------

    override suspend fun sendMessage(message: Message, path: String): Boolean {
        val nodeIds = _connectedNodeIds.value
        if (nodeIds.isEmpty()) {
            Log.w(tag, "sendMessage: no connected nodes — message dropped")
            return false
        }
        val json = gson.toJson(message)
        val data = json.toByteArray(Charsets.UTF_8)
        var successCount = 0
        for (nodeId in nodeIds) {
            try {
                messageClient.sendMessage(nodeId, path, data).await()
                successCount++
                Log.d(tag, "Message sent to $nodeId at $path")
            } catch (e: Exception) {
                Log.e(tag, "Failed to send message to $nodeId: ${e.message}", e)
            }
        }
        return successCount > 0
    }

    override suspend fun sendData(data: ByteArray, path: String) {
        try {
            val request = PutDataMapRequest.create(path).apply {
                dataMap.putByteArray("payload", data)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
            Log.d(tag, "Data pushed to DataClient at $path (${data.size} bytes)")
        } catch (e: Exception) {
            Log.e(tag, "Failed to push data to $path: ${e.message}", e)
        }
    }

    override suspend fun refreshConnectedNodes() {
        try {
            val nodes = nodeClient.connectedNodes.await()
            _connectedNodeIds.value = nodes.map { it.id }
            Log.d(tag, "Connected nodes: ${_connectedNodeIds.value}")
        } catch (e: Exception) {
            Log.e(tag, "refreshConnectedNodes failed: ${e.message}", e)
        }
    }

    override fun destroy() {
        messageClient.removeListener(this)
        Log.d(tag, "WearableRepository destroyed")
    }
}
