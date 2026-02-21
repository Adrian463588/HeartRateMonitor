package com.example.samplewearmobileapp.sensor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Connection state for a sensor device.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/**
 * A single ECG sample with its timestamp.
 */
data class EcgSample(
    val timestampMs: Long,
    val microVolts: Double,
    val isPeak: Boolean = false
)

/**
 * A single PPG sample with its timestamp.
 */
data class PpgSample(
    val timestampMs: Long,
    val value: Int,
    val channel: PpgChannel
)

enum class PpgChannel {
    GREEN, IR, RED
}

/**
 * Interface for managing the Polar H10 ECG sensor connection.
 *
 * Provides reactive streams for ECG data and connection state,
 * with built-in auto-reconnection logic.
 */
interface PolarEcgManager {

    /** Current connection state of the Polar device. */
    val connectionState: StateFlow<ConnectionState>

    /** Device info (populated after connection). */
    val deviceName: StateFlow<String>
    val batteryLevel: StateFlow<Int>
    val firmware: StateFlow<String>

    /**
     * Connect to the Polar device with the given ID.
     * Will initiate auto-reconnection on unexpected disconnection.
     *
     * @param deviceId The Polar device identifier (e.g., "A0B1C2D3").
     */
    suspend fun connect(deviceId: String)

    /**
     * Disconnect from the Polar device.
     * Cancels auto-reconnection attempts.
     */
    suspend fun disconnect()

    /**
     * Start streaming ECG data from the connected device.
     *
     * @return A Flow of [EcgSample] values at 130 Hz.
     * @throws IllegalStateException if device is not connected.
     */
    fun startEcgStream(): Flow<EcgSample>

    /**
     * Stop streaming ECG data.
     */
    fun stopEcgStream()

    /**
     * Stream of RR intervals in milliseconds (from HR notifications).
     */
    fun rrIntervalFlow(): Flow<Int>
}

/**
 * Interface for receiving PPG data from the Galaxy Watch 5
 * via the Wearable Data Layer API.
 */
interface WearPpgManager {

    /** Current connection state with the Wear device. */
    val connectionState: StateFlow<ConnectionState>

    /** Flow of PPG Green samples (25 Hz). */
    val ppgGreenFlow: Flow<PpgSample>

    /** Flow of PPG IR samples (100 Hz). */
    val ppgIrFlow: Flow<PpgSample>

    /** Flow of PPG Red samples (100 Hz). */
    val ppgRedFlow: Flow<PpgSample>

    /**
     * Start listening for PPG data from the connected watch.
     * Registers DataClient and MessageClient listeners.
     */
    fun startListening()

    /**
     * Stop listening and unregister all listeners.
     */
    fun stopListening()

    /**
     * Send a command to the watch (start/stop/pause trackers).
     */
    suspend fun sendCommand(command: WearCommand)
}

/**
 * Commands that can be sent to the Wear module.
 */
sealed class WearCommand {
    object StartAllTrackers : WearCommand()
    object StopAllTrackers : WearCommand()
    object PauseAllTrackers : WearCommand()
    data class ToggleTracker(val channel: PpgChannel) : WearCommand()
}
