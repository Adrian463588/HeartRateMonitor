package com.example.samplewearmobileapp.domain.model

/**
 * Represents the connection state of a Polar BLE device.
 *
 * Used by [PolarRepository] to expose device lifecycle events as a [StateFlow].
 */
sealed class DeviceConnectionState {

    /** No device is connected or connecting. */
    data object Disconnected : DeviceConnectionState()

    /**
     * A connection attempt is in progress.
     * @param deviceId The Polar device ID being connected to.
     */
    data class Connecting(val deviceId: String) : DeviceConnectionState()

    /**
     * Device is fully connected and streams can be started.
     * @param deviceId Polar device ID (e.g. "A0B1C2").
     * @param name Human-readable device name (e.g. "Polar H10").
     * @param firmware Device firmware version string.
     * @param batteryLevel Battery percentage [0–100], or null if not yet received.
     * @param address BLE MAC address.
     */
    data class Connected(
        val deviceId: String,
        val name: String = "Unknown",
        val firmware: String = "NA",
        val batteryLevel: Int? = null,
        val address: String = "NA"
    ) : DeviceConnectionState()
}
