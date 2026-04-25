package com.example.samplewearmobileapp.data.repository

import android.content.Context
import android.util.Log
import com.example.samplewearmobileapp.domain.model.DeviceConnectionState
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.model.EcgSample
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarHrData
import io.reactivex.rxjava3.disposables.Disposable
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
import java.util.UUID

/**
 * Production implementation of [PolarRepository] backed by Polar BLE SDK v6.16.1.
 *
 * **Architecture notes:**
 * - The Polar SDK v6 uses RxJava 3 internally for streaming.
 * - We bridge the RxJava [Disposable] streams into Kotlin [Flow]
 *   so that all callers (ViewModel, tests) use only Kotlin coroutines — no RxJava leaks.
 * - The [SupervisorJob] scope ensures stream failures don't cascade to the parent.
 *
 * **SDK v6 API notes:**
 * - Feature set uses [PolarBleApi.PolarBleSdkFeature] nested enum.
 * - Online streaming requires [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING].
 * - ECG samples are typed as a sealed class; voltage is on the [EcgSample] subtype.
 * - Streaming returns [Disposable] (not a coroutine Job) — managed here internally.
 *
 * @param context Application context used to initialise the Polar SDK.
 */
class PolarRepositoryImpl(context: Context) : PolarRepository {

    private val tag = "PolarRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connectionState =
        MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Disconnected)
    override val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _ecgSamples = MutableSharedFlow<List<Double>>(extraBufferCapacity = 64)
    override val ecgSamples: Flow<List<Double>> = _ecgSamples.asSharedFlow()

    private val _heartRate = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    override val heartRate: Flow<Int> = _heartRate.asSharedFlow()

    private var currentDeviceId: String = ""

    /** RxJava disposable for the active ECG stream. Null when stream is inactive. */
    private var ecgDisposable: Disposable? = null

    /**
     * Polar BLE SDK v6 instance.
     *
     * SDK v6 uses [PolarBleApi.PolarBleSdkFeature] — the older `DeviceStreamingFeature`
     * enum was removed in this version. Use [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING]
     * to enable ECG/PPG streaming capabilities.
     */
    private val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(
        context = context.applicationContext,
        features = setOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO
        )
    )

    init {
        api.setApiCallback(object : PolarBleApiCallback() {

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(tag, "Connecting: ${polarDeviceInfo.deviceId}")
                _connectionState.value = DeviceConnectionState.Connecting(polarDeviceInfo.deviceId)
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(tag, "Connected: ${polarDeviceInfo.deviceId}")
                _connectionState.value = DeviceConnectionState.Connected(
                    deviceId = polarDeviceInfo.deviceId,
                    name = polarDeviceInfo.name,
                    address = polarDeviceInfo.address
                )
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(tag, "Disconnected: ${polarDeviceInfo.deviceId}")
                _connectionState.value = DeviceConnectionState.Disconnected
                stopEcgDisposable()
            }

            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(tag, "BLE power: $powered")
                if (!powered) _connectionState.value = DeviceConnectionState.Disconnected
            }

            override fun hrNotificationReceived(
                identifier: String,
                data: PolarHrData.PolarHrSample
            ) {
                scope.launch { _heartRate.emit(data.hr) }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                updateConnectedState(identifier) { it.copy(batteryLevel = level) }
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                updateConnectedState(identifier) { it.copy(firmware = value) }
            }

            override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
                // Additional DIS info (key-value) — ignored; UUID overload handles firmware
            }

            override fun htsNotificationReceived(
                identifier: String,
                data: PolarHealthThermometerData
            ) {
                // Health Thermometer Service — not used in this application
            }

            override fun bleSdkFeaturesReadiness(
                identifier: String,
                ready: List<PolarBleApi.PolarBleSdkFeature>,
                unavailable: List<PolarBleApi.PolarBleSdkFeature>
            ) {
                Log.d(tag, "Features ready=$ready unavailable=$unavailable for $identifier")
            }

            override fun bleSdkFeatureReady(
                identifier: String,
                feature: PolarBleApi.PolarBleSdkFeature
            ) {
                Log.d(tag, "Feature ready: $feature on $identifier")
            }
        })
    }

    // -------------------------------------------------------------------------
    // PolarRepository interface
    // -------------------------------------------------------------------------

    override fun connect(deviceId: String) {
        if (deviceId.isBlank()) return
        try {
            currentDeviceId = deviceId
            api.connectToDevice(deviceId)
        } catch (e: PolarInvalidArgument) {
            Log.e(tag, "Invalid deviceId '$deviceId': ${e.message}")
        }
    }

    override fun disconnect() {
        if (currentDeviceId.isBlank()) return
        try {
            api.disconnectFromDevice(currentDeviceId)
        } catch (e: PolarInvalidArgument) {
            Log.e(tag, "Disconnect error: ${e.message}")
        }
    }

    override suspend fun startEcgStream() {
        val deviceId = (connectionState.value as? DeviceConnectionState.Connected)?.deviceId
            ?: run { Log.w(tag, "startEcgStream: no connected device"); return }

        if (ecgDisposable?.isDisposed == false) {
            Log.d(tag, "ECG stream already active")
            return
        }

        // SDK v6: requestStreamSettings + startEcgStreaming are on the PolarOnlineStreamingApi
        ecgDisposable = api.requestStreamSettings(
            deviceId,
            PolarBleApi.PolarDeviceDataType.ECG
        ).flatMapPublisher { settings ->
            api.startEcgStreaming(deviceId, settings.maxSettings())
        }.subscribe(
            { ecgData: PolarEcgData ->
                // SDK v6: voltage is on the EcgSample sealed subclass, not the base class
                val samples = ecgData.samples
                    .filterIsInstance<EcgSample>()
                    .map { it.voltage.toDouble() * 0.001 } // μV → mV
                scope.launch { _ecgSamples.emit(samples) }
            },
            { error -> Log.e(tag, "ECG stream error: ${error.message}", error) }
        )
        Log.d(tag, "ECG stream started for $deviceId")
    }

    override suspend fun stopEcgStream() {
        stopEcgDisposable()
        Log.d(tag, "ECG stream stopped")
    }

    override fun destroy() {
        stopEcgDisposable()
        api.shutDown()
        Log.d(tag, "PolarRepository destroyed")
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun stopEcgDisposable() {
        ecgDisposable?.takeIf { !it.isDisposed }?.dispose()
        ecgDisposable = null
    }

    private fun updateConnectedState(
        identifier: String,
        update: (DeviceConnectionState.Connected) -> DeviceConnectionState.Connected
    ) {
        val current = _connectionState.value
        if (current is DeviceConnectionState.Connected && current.deviceId == identifier) {
            _connectionState.value = update(current)
        }
    }
}
