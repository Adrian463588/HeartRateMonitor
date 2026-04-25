package com.example.samplewearmobileapp.data.repository

import com.example.samplewearmobileapp.domain.model.DeviceConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for all Polar BLE device interactions.
 *
 * Concrete implementations wrap the Polar BLE SDK while this interface
 * ensures the ViewModel depends only on an abstraction (DIP).
 *
 * **Polar SDK v7 note:** All streaming methods return [Flow] and
 * connection events are suspend functions — no RxJava needed.
 */
interface PolarRepository {

    /** Emits the current connection state of the Polar device. */
    val connectionState: StateFlow<DeviceConnectionState>

    /**
     * Emits batches of ECG samples (in μV) as they arrive from the device.
     * Each emission is a list of samples from one Polar SDK data frame.
     */
    val ecgSamples: Flow<List<Double>>

    /**
     * Emits the latest heart rate value from the device HR service.
     * Emits `null` before the first reading arrives.
     */
    val heartRate: Flow<Int>

    /**
     * Initiates a BLE connection to the device with the given [deviceId].
     * Results are emitted via [connectionState].
     */
    fun connect(deviceId: String)

    /** Disconnects from the currently connected device. */
    fun disconnect()

    /**
     * Starts streaming ECG data. Emits via [ecgSamples].
     * No-op if ECG is already streaming.
     *
     * @throws IllegalStateException if no device is connected.
     */
    suspend fun startEcgStream()

    /**
     * Stops the ECG data stream.
     * No-op if ECG is not currently streaming.
     */
    suspend fun stopEcgStream()

    /** Cleans up all SDK resources. Call from the ViewModel's [onCleared]. */
    fun destroy()
}
