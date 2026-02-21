package com.example.samplewearmobileapp.sensor

import android.content.Context
import android.util.Log
import com.example.samplewearmobileapp.Constants
import com.example.samplewearmobileapp.utils.TimestampHelper
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.DeviceStreamingFeature
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * Concrete implementation of [PolarEcgManager] wrapping the Polar BLE SDK.
 *
 * Handles:
 * - BLE connection lifecycle via `PolarBleApiCallback`
 * - ECG streaming via RxJava → Kotlin SharedFlow bridge
 * - Auto-reconnection with exponential backoff (5 retries, 2s→32s)
 * - Phone-clock timestamp generation via [TimestampHelper]
 *
 * Extracted from the monolithic `MainActivity.setupPolar()` and
 * `toggleEcgStream()` methods.
 */
class PolarEcgManagerImpl(
    private val context: Context
) : PolarEcgManager {

    companion object {
        private const val TAG = "PolarEcgManagerImpl"
        private const val MAX_RETRIES = 5
        private const val INITIAL_RETRY_DELAY_MS = 2000L
    }

    // === Public StateFlows (interface contract) ===
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceName = MutableStateFlow("")
    override val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _batteryLevel = MutableStateFlow(-1)
    override val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _firmware = MutableStateFlow("")
    override val firmware: StateFlow<String> = _firmware.asStateFlow()

    // === Internal State ===
    private var polarApi: PolarBleApi? = null
    private var currentDeviceId: String? = null
    private var ecgDisposable: Disposable? = null
    private var reconnectJob: Job? = null
    private var retryCount = 0
    private var intentionalDisconnect = false
    private var streamingReady = false

    // ECG sample buffer — shared flow so multiple collectors can observe
    private val _ecgSamples = MutableSharedFlow<EcgSample>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // RR interval buffer
    private val _rrIntervals = MutableSharedFlow<Int>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Scope for reconnection coroutines
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ===== Interface Implementation =====

    override suspend fun connect(deviceId: String) {
        intentionalDisconnect = false
        currentDeviceId = deviceId
        retryCount = 0

        if (polarApi == null) {
            initializePolarApi()
        }

        _connectionState.value = ConnectionState.CONNECTING
        Log.i(TAG, "Connecting to Polar device: $deviceId")

        try {
            polarApi?.connectToDevice(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to $deviceId", e)
            _connectionState.value = ConnectionState.ERROR
            scheduleReconnect()
        }
    }

    override suspend fun disconnect() {
        intentionalDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null

        stopEcgStream()

        currentDeviceId?.let { id ->
            try {
                polarApi?.disconnectFromDevice(id)
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from $id", e)
            }
        }

        _connectionState.value = ConnectionState.DISCONNECTED
        Log.i(TAG, "Disconnected from Polar device")
    }

    override fun startEcgStream(): Flow<EcgSample> {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot start ECG stream — not connected (state=${_connectionState.value})")
        }

        val deviceId = currentDeviceId ?: run {
            Log.e(TAG, "No device ID set for ECG stream")
            return emptyFlow()
        }

        if (ecgDisposable != null) {
            Log.w(TAG, "ECG stream already running, returning existing flow")
            return _ecgSamples.asSharedFlow()
        }

        // Reset ECG timestamps for phone-clock anchoring
        TimestampHelper.resetEcgTimestamps()

        val timeZone = TimeZone.getTimeZone("UTC")
        val calNow = Calendar.getInstance(timeZone)

        Log.i(TAG, "Starting ECG stream — setLocalTime to ${calNow.time}")

        ecgDisposable = polarApi!!.setLocalTime(deviceId, calNow)
            .andThen(
                polarApi!!.requestStreamSettings(
                    deviceId,
                    DeviceStreamingFeature.ECG
                )
            )
            .toFlowable()
            .flatMap { sensorSetting: PolarSensorSetting ->
                polarApi!!.startEcgStreaming(
                    deviceId,
                    sensorSetting.maxSettings()
                )
            }
            .subscribe(
                { polarEcgData: PolarEcgData ->
                    // Convert each sample to EcgSample with phone-clock timestamp
                    for (sample in polarEcgData.samples) {
                        val timestampMs = TimestampHelper.nextEcgTimestamp(
                            Constants.ECG_SAMPLE_RATE
                        )
                        val microVolts = sample.voltage.toDouble()
                        val ecgSample = EcgSample(
                            timestampMs = timestampMs,
                            microVolts = microVolts
                        )
                        _ecgSamples.tryEmit(ecgSample)
                    }
                },
                { throwable: Throwable ->
                    Log.e(TAG, "ECG stream error: ${throwable.localizedMessage}", throwable)
                    ecgDisposable = null
                },
                {
                    Log.i(TAG, "ECG streaming completed")
                    ecgDisposable = null
                }
            )

        Log.i(TAG, "ECG stream started")
        return _ecgSamples.asSharedFlow()
    }

    override fun stopEcgStream() {
        ecgDisposable?.dispose()
        ecgDisposable = null
        Log.i(TAG, "ECG stream stopped")
    }

    override fun rrIntervalFlow(): Flow<Int> = _rrIntervals.asSharedFlow()

    /**
     * Shuts down the Polar API entirely. Call on app destruction.
     */
    fun shutdown() {
        reconnectJob?.cancel()
        scope.cancel()
        stopEcgStream()
        try {
            polarApi?.shutDown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down Polar API", e)
        }
        polarApi = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.i(TAG, "Polar API shut down")
    }

    // ===== Private: Polar API Setup =====

    private fun initializePolarApi() {
        polarApi = PolarBleApiDefaultImpl.defaultImplementation(
            context,
            PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING or
                    PolarBleApi.FEATURE_BATTERY_INFO or
                    PolarBleApi.FEATURE_DEVICE_INFO or
                    PolarBleApi.FEATURE_HR
        )

        polarApi!!.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BLE power state changed: $powered")
                if (!powered) {
                    _connectionState.value = ConnectionState.ERROR
                }
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.i(TAG, "Device connected: ${polarDeviceInfo.deviceId}")
                _deviceName.value = polarDeviceInfo.name
                _connectionState.value = ConnectionState.CONNECTED
                retryCount = 0 // Reset retry count on successful connection
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.i(TAG, "Device disconnected: ${polarDeviceInfo.deviceId}")
                streamingReady = false
                ecgDisposable?.dispose()
                ecgDisposable = null

                if (!intentionalDisconnect) {
                    _connectionState.value = ConnectionState.RECONNECTING
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }

            override fun streamingFeaturesReady(
                identifier: String,
                features: Set<DeviceStreamingFeature>
            ) {
                if (features.contains(DeviceStreamingFeature.ECG)) {
                    streamingReady = true
                    Log.i(TAG, "ECG streaming feature ready for $identifier")
                }
            }

            override fun hrFeatureReady(identifier: String) {
                Log.d(TAG, "HR feature ready: $identifier")
            }

            override fun disInformationReceived(
                identifier: String,
                uuid: UUID,
                value: String
            ) {
                // Firmware version UUID
                if (uuid == UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")) {
                    _firmware.value = value.trim()
                    Log.i(TAG, "Firmware: ${_firmware.value}")
                }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                _batteryLevel.value = level
                Log.i(TAG, "Battery level: $level%")
            }

            override fun hrNotificationReceived(
                identifier: String,
                data: PolarHrData
            ) {
                // Emit RR intervals for HRV analysis
                for (rr in data.rrsMs) {
                    _rrIntervals.tryEmit(rr)
                }
            }
        })

        Log.i(TAG, "Polar API initialized")
    }

    // ===== Private: Auto-Reconnection =====

    private fun scheduleReconnect() {
        if (retryCount >= MAX_RETRIES) {
            Log.e(TAG, "Max reconnection attempts ($MAX_RETRIES) reached")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl retryCount)
        retryCount++
        Log.w(TAG, "Scheduling reconnect #$retryCount in ${delayMs}ms")

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            currentDeviceId?.let { id ->
                Log.i(TAG, "Reconnect attempt #$retryCount for $id")
                _connectionState.value = ConnectionState.RECONNECTING
                try {
                    polarApi?.connectToDevice(id)
                } catch (e: Exception) {
                    Log.e(TAG, "Reconnect #$retryCount failed", e)
                    scheduleReconnect()
                }
            }
        }
    }
}
