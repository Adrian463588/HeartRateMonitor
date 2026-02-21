package com.example.samplewearmobileapp.sensor

import android.content.Context
import android.util.Log
import com.example.samplewearmobileapp.constants.MessagePath
import com.example.samplewearmobileapp.constants.codes.ActivityCode
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.models.PpgData
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

/**
 * Concrete implementation of [WearPpgManager] that bridges the
 * Wearable Data Layer API (DataClient/MessageClient) to Kotlin Flows.
 *
 * Listens for PPG data batches sent by the Galaxy Watch 5 wear module
 * via DataClient, deserializes them using Gson, and emits individual
 * [PpgSample] objects for each channel (Green/IR/Red).
 *
 * Sends commands to the wear module via MessageClient to control
 * tracker start/stop/pause.
 *
 * Extracted from `MainActivity.onDataArrived()`, `onMessageArrived()`,
 * and `sendMessage()`.
 */
class WearPpgManagerImpl(
    private val context: Context,
    private val gson: Gson
) : WearPpgManager {

    companion object {
        private const val TAG = "WearPpgManagerImpl"
        private const val SENDER_NAME = "MobileApp"
    }

    // === Public StateFlows ===
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // === PPG SharedFlows ===
    private val _ppgGreenFlow = MutableSharedFlow<PpgSample>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val ppgGreenFlow: Flow<PpgSample> = _ppgGreenFlow.asSharedFlow()

    private val _ppgIrFlow = MutableSharedFlow<PpgSample>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val ppgIrFlow: Flow<PpgSample> = _ppgIrFlow.asSharedFlow()

    private val _ppgRedFlow = MutableSharedFlow<PpgSample>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val ppgRedFlow: Flow<PpgSample> = _ppgRedFlow.asSharedFlow()

    // === Internal ===
    private var dataClient: DataClient? = null
    private var messageClient: MessageClient? = null
    private var nodeClient: NodeClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * DataClient listener that deserializes incoming PPG batches.
     */
    private val dataListener = DataClient.OnDataChangedListener { dataEvents ->
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                processDataEvent(event)
            }
        }
    }

    /**
     * MessageClient listener for wear module state notifications.
     */
    private val messageListener = MessageClient.OnMessageReceivedListener { messageEvent ->
        processMessageEvent(messageEvent)
    }

    // ===== Interface Implementation =====

    override fun startListening() {
        Log.i(TAG, "Starting PPG data listeners")

        dataClient = Wearable.getDataClient(context).also {
            it.addListener(dataListener)
        }
        messageClient = Wearable.getMessageClient(context).also {
            it.addListener(messageListener)
        }
        nodeClient = Wearable.getNodeClient(context)

        // Check if watch is connected
        scope.launch {
            checkWatchConnection()
        }
    }

    override fun stopListening() {
        Log.i(TAG, "Stopping PPG data listeners")

        dataClient?.removeListener(dataListener)
        messageClient?.removeListener(messageListener)

        dataClient = null
        messageClient = null
        nodeClient = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun sendCommand(command: WearCommand) {
        val message = when (command) {
            is WearCommand.StartAllTrackers -> {
                Message(SENDER_NAME, ActivityCode.START_ACTIVITY)
            }
            is WearCommand.StopAllTrackers -> {
                Message(SENDER_NAME, ActivityCode.STOP_ACTIVITY)
            }
            is WearCommand.PauseAllTrackers -> {
                Message(SENDER_NAME, ActivityCode.PAUSE_ACTIVITY)
            }
            is WearCommand.ToggleTracker -> {
                // Use extra code to specify which tracker
                val extraCode = when (command.channel) {
                    PpgChannel.GREEN -> 1
                    PpgChannel.IR -> 2
                    PpgChannel.RED -> 3
                }
                Message(SENDER_NAME, ActivityCode.START_ACTIVITY, extraCode)
            }
        }

        sendMessageToWatch(message, MessagePath.COMMAND)
    }

    // ===== Private: Data Processing =====

    private fun processDataEvent(dataEvent: DataEvent) {
        val path = dataEvent.dataItem.uri.path ?: return
        val rawData = dataEvent.dataItem.data ?: return

        try {
            val jsonString = String(rawData)

            when (path) {
                MessagePath.DATA_PPG_GREEN -> {
                    val ppgData = gson.fromJson(jsonString, PpgData::class.java)
                    emitPpgBatch(ppgData, PpgChannel.GREEN, _ppgGreenFlow)
                }
                MessagePath.DATA_PPG_IR -> {
                    val ppgData = gson.fromJson(jsonString, PpgData::class.java)
                    emitPpgBatch(ppgData, PpgChannel.IR, _ppgIrFlow)
                }
                MessagePath.DATA_PPG_RED -> {
                    val ppgData = gson.fromJson(jsonString, PpgData::class.java)
                    emitPpgBatch(ppgData, PpgChannel.RED, _ppgRedFlow)
                }
                MessagePath.DATA_HR -> {
                    Log.d(TAG, "HR data received (not emitted via PPG flow)")
                }
                else -> {
                    Log.d(TAG, "Unknown data path: $path")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing data event on path=$path", e)
        }
    }

    /**
     * Converts a batch of PPG values from the watch into individual
     * [PpgSample] objects and emits them to the appropriate SharedFlow.
     */
    private fun emitPpgBatch(
        ppgData: PpgData,
        channel: PpgChannel,
        flow: MutableSharedFlow<PpgSample>
    ) {
        for (i in 0 until ppgData.size) {
            val sample = PpgSample(
                timestampMs = ppgData.timestamps[i],
                value = ppgData.ppgValues[i],
                channel = channel
            )
            flow.tryEmit(sample)
        }
        Log.d(TAG, "${channel.name} batch emitted: ${ppgData.size} samples")
    }

    private fun processMessageEvent(messageEvent: MessageEvent) {
        val path = messageEvent.path
        try {
            val message = gson.fromJson(String(messageEvent.data), Message::class.java)

            when (path) {
                MessagePath.INFO -> {
                    when (message.code) {
                        ActivityCode.START_ACTIVITY -> {
                            _connectionState.value = ConnectionState.CONNECTED
                            Log.i(TAG, "Wear module started tracking")
                        }
                        ActivityCode.STOP_ACTIVITY -> {
                            Log.i(TAG, "Wear module stopped tracking")
                        }
                        ActivityCode.PAUSE_ACTIVITY -> {
                            Log.i(TAG, "Wear module paused tracking")
                        }
                    }
                }
                MessagePath.COMMAND -> {
                    Log.d(TAG, "Command from wear: code=${message.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message on path=$path", e)
        }
    }

    // ===== Private: Message Sending =====

    /**
     * Sends a message to all connected watch nodes using GMS Task API
     * with addOnSuccessListener/addOnFailureListener (no coroutines-play-services needed).
     */
    private fun sendMessageToWatch(message: Message, path: String) {
        val nc = nodeClient ?: run {
            Log.w(TAG, "NodeClient not initialized")
            return
        }

        nc.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected watch nodes found")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@addOnSuccessListener
                }

                val bytes = gson.toJson(message).toByteArray()
                for (node in nodes) {
                    messageClient?.sendMessage(node.id, path, bytes)
                        ?.addOnSuccessListener {
                            Log.d(TAG, "Message sent to ${node.displayName}: " +
                                    "path=$path, code=${message.code}")
                        }
                        ?.addOnFailureListener { e ->
                            Log.e(TAG, "Failed to send message to ${node.displayName}", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get connected nodes", e)
            }
    }

    // ===== Private: Connection Check =====

    private fun checkWatchConnection() {
        val nc = nodeClient ?: return

        nc.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isNotEmpty()) {
                    _connectionState.value = ConnectionState.CONNECTED
                    Log.i(TAG, "Watch connected: ${nodes.map { it.displayName }}")
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    Log.w(TAG, "No watch nodes detected")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking watch connection", e)
                _connectionState.value = ConnectionState.ERROR
            }
    }

    /**
     * Cleans up all resources. Call on app destruction.
     */
    fun shutdown() {
        stopListening()
        scope.cancel()
    }
}
