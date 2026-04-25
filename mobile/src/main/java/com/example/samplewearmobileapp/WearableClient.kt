package com.example.samplewearmobileapp

import android.content.Context
import android.util.Log
import com.example.samplewearmobileapp.constants.MessagePath
import com.example.samplewearmobileapp.models.HeartData
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.models.PpgData
import com.example.samplewearmobileapp.models.PpgType
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Manages all Wearable Data Layer communication with the paired Galaxy Watch.
 *
 * **Replaces:** deprecated [com.google.android.gms.common.api.GoogleApiClient] +
 * `Wearable.MessageApi` / `Wearable.DataApi` chains.
 *
 * **Uses:** modern [MessageClient], [DataClient], [NodeClient] (play-services-wearable 18.x+).
 *
 * **SRP:** this class owns the *only* Wearable IPC concern — routing messages and
 * data-layer items to/from the callback. [MainActivity] no longer implements
 * `GoogleApiClient.ConnectionCallbacks`.
 *
 * **Thread-safety:** Message/Data parsing is dispatched to [Dispatchers.IO];
 * callback delivery is dispatched to [Dispatchers.Main] so callers can update UI
 * directly from callback implementations.
 */
class WearableClient(private val context: Context) :
    MessageClient.OnMessageReceivedListener,
    DataClient.OnDataChangedListener {

    // -------------------------------------------------------------------------
    // Callback interface
    // -------------------------------------------------------------------------

    /**
     * Callers (typically [MainActivity]) implement this to receive parsed data.
     * All methods are called on the **main thread**.
     */
    interface Callback {
        fun onPpgDataReceived(type: PpgType, data: PpgData)
        fun onHeartDataReceived(data: HeartData)
        fun onMessageReceived(path: String, message: Message)
        fun onNodeConnected(nodes: List<Node>)
        fun onNodeDisconnected()
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private val gson = Gson()  // Shared instance — eliminates per-call allocation

    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }
    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(context) }

    private var callback: Callback? = null
    private var scope: CoroutineScope? = null

    /** Set of connected node IDs (updated on [connect] and node discovery). */
    var connectedNodes: List<Node> = emptyList()
        private set

    val isConnected: Boolean get() = connectedNodes.isNotEmpty()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Registers Wearable listeners and discovers currently connected nodes.
     * Call from [android.app.Activity.onStart] or equivalent.
     *
     * @param scope   A [CoroutineScope] that outlives this call (e.g. `lifecycleScope`).
     * @param callback Receives parsed data on the main thread.
     */
    fun connect(scope: CoroutineScope, callback: Callback) {
        this.scope = scope
        this.callback = callback
        messageClient.addListener(this)
        dataClient.addListener(this)
        // Discover connected nodes asynchronously
        scope.launch(Dispatchers.IO) {
            runCatching {
                nodeClient.connectedNodes.await()
            }.onSuccess { nodes ->
                connectedNodes = nodes
                scope.launch(Dispatchers.Main) {
                    if (nodes.isNotEmpty()) callback.onNodeConnected(nodes)
                    else callback.onNodeDisconnected()
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to get connected nodes", e)
                scope.launch(Dispatchers.Main) { callback.onNodeDisconnected() }
            }
        }
    }

    /**
     * Unregisters Wearable listeners.
     * Call from [android.app.Activity.onStop] or equivalent.
     */
    fun disconnect() {
        runCatching { messageClient.removeListener(this) }
        runCatching { dataClient.removeListener(this) }
        callback = null
        scope = null
    }

    // -------------------------------------------------------------------------
    // Sending
    // -------------------------------------------------------------------------

    /**
     * Sends [message] to all connected nodes at [path].
     * Fire-and-forget with error logging. Runs on [Dispatchers.IO].
     */
    fun sendMessage(message: Message, path: String) {
        val bytes = gson.toJson(message).toByteArray()
        scope?.launch(Dispatchers.IO) {
            connectedNodes.forEach { node ->
                runCatching {
                    messageClient.sendMessage(node.id, path, bytes).await()
                }.onFailure { e ->
                    Log.e(TAG, "sendMessage failed to node=${node.id} path=$path", e)
                }
            }
        } ?: Log.w(TAG, "sendMessage called before connect()")
    }

    // -------------------------------------------------------------------------
    // MessageClient.OnMessageReceivedListener
    // -------------------------------------------------------------------------

    override fun onMessageReceived(event: MessageEvent) {
        val cb = callback ?: return
        val s = scope ?: return
        s.launch(Dispatchers.IO) {
            runCatching {
                gson.fromJson(String(event.data), Message::class.java)
            }.onSuccess { msg ->
                s.launch(Dispatchers.Main) {
                    cb.onMessageReceived(event.path, msg)
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to parse message at path=${event.path}", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // DataClient.OnDataChangedListener
    // -------------------------------------------------------------------------

    override fun onDataChanged(events: DataEventBuffer) {
        val cb = callback ?: run { events.release(); return }
        // Snapshot data before the buffer is released
        val snapshots = events.map { event ->
            Triple(event.type, event.dataItem.uri.path, event.dataItem.data?.copyOf())
        }
        events.release()

        scope?.launch(Dispatchers.IO) {
            snapshots.forEach { (type, path, rawData) ->
                if (type != DataEvent.TYPE_CHANGED || rawData == null) return@forEach
                handleDataEvent(path, rawData, cb)
            }
        }
    }

    private suspend fun handleDataEvent(path: String?, raw: ByteArray, cb: Callback) {
        when (path) {
            MessagePath.DATA_HR -> runCatching {
                gson.fromJson(String(raw), HeartData::class.java)
            }.onSuccess { data ->
                scope?.launch(Dispatchers.Main) { cb.onHeartDataReceived(data) }
            }.onFailure { e -> Log.e(TAG, "Failed to parse HeartData", e) }

            MessagePath.DATA_PPG_GREEN -> parsePpgAndDeliver(raw, PpgType.PPG_GREEN, cb)
            MessagePath.DATA_PPG_IR    -> parsePpgAndDeliver(raw, PpgType.PPG_IR, cb)
            MessagePath.DATA_PPG_RED   -> parsePpgAndDeliver(raw, PpgType.PPG_RED, cb)

            else -> Log.d(TAG, "Unknown data path: $path")
        }
    }

    private fun parsePpgAndDeliver(raw: ByteArray, type: PpgType, cb: Callback) {
        runCatching {
            gson.fromJson(String(raw), PpgData::class.java)
        }.onSuccess { data ->
            scope?.launch(Dispatchers.Main) { cb.onPpgDataReceived(type, data) }
        }.onFailure { e -> Log.e(TAG, "Failed to parse PpgData for $type", e) }
    }

    companion object {
        private const val TAG = "WearableClient"
    }
}
