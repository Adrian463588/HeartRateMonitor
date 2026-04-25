package com.example.samplewearmobileapp.polar

import com.example.samplewearmobileapp.HrPlotter
import com.example.samplewearmobileapp.MainActivity
import com.example.samplewearmobileapp.PpgUiState
import com.example.samplewearmobileapp.UiStateManager
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID

/**
 * Unit tests for [PolarCallbacks].
 *
 * Uses Mockito to isolate [MainActivity] entirely — no Android framework required.
 *
 * **Threading:** [PolarCallbacks] posts UI work via [MainActivity.runOnUiThread].
 * In these tests [runOnUiThread] is stubbed to execute the [Runnable] inline,
 * so we can assert both that the runOnUiThread call was made AND what the
 * runnable would have done.
 */
class PolarCallbacksTest {

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    private lateinit var activity: MainActivity
    private lateinit var uiStateManager: UiStateManager
    private lateinit var hrPlotter: HrPlotter
    private lateinit var callbacks: PolarCallbacks

    /** A fake PolarDeviceInfo. The SDK class is a data class — we can use a real instance. */
    private val fakeDeviceInfo = PolarDeviceInfo(
        deviceId  = "ABCD1234",
        address   = "AA:BB:CC:DD:EE:FF",
        rssi      = -60,
        name      = "Polar H10",
        isConnectable = true
    )

    /** A minimal PolarHrData (hr=75, no RR intervals). */
    private val fakeHrData = PolarHrData(
        hr                   = 75,
        rrs                  = emptyList(),
        contactStatus        = true,
        contactStatusSupported = true,
        rrAvailable          = false
    )

    /** A PolarHrData with RR intervals. */
    private val fakeHrDataWithRr = PolarHrData(
        hr                   = 80,
        rrs                  = listOf(1024, 1024),  // 1000 ms each
        contactStatus        = true,
        contactStatusSupported = true,
        rrAvailable          = true
    )

    @Before
    fun setUp() {
        activity       = mock(MainActivity::class.java)
        uiStateManager = mock(UiStateManager::class.java)
        hrPlotter      = mock(HrPlotter::class.java)

        // Wire mocked components onto the mocked activity
        activity.uiStateManager = uiStateManager
        activity.hrPlotter      = hrPlotter

        // Stub runOnUiThread to execute the Runnable inline (synchronous for testing)
        `when`(activity.runOnUiThread(any(Runnable::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Runnable).run()
            null
        }

        callbacks = PolarCallbacks(activity)
    }

    // =========================================================================
    // deviceConnected
    // =========================================================================

    @Test
    fun `deviceConnected sets isPolarDeviceConnected to true`() {
        callbacks.deviceConnected(fakeDeviceInfo)
        assertTrue(activity.isPolarDeviceConnected)
    }

    @Test
    fun `deviceConnected updates deviceId from info`() {
        callbacks.deviceConnected(fakeDeviceInfo)
        assertEquals("ABCD1234", activity.deviceId)
    }

    @Test
    fun `deviceConnected updates deviceName from info`() {
        callbacks.deviceConnected(fakeDeviceInfo)
        assertEquals("Polar H10", activity.deviceName)
    }

    @Test
    fun `deviceConnected updates deviceAddress from info`() {
        callbacks.deviceConnected(fakeDeviceInfo)
        assertEquals("AA:BB:CC:DD:EE:FF", activity.deviceAddress)
    }

    @Test
    fun `deviceConnected posts to runOnUiThread`() {
        callbacks.deviceConnected(fakeDeviceInfo)
        verify(activity).runOnUiThread(any(Runnable::class.java))
    }

    @Test
    fun `deviceConnected sets ECG UI state to Connected`() {
        callbacks.deviceConnected(fakeDeviceInfo)
        verify(uiStateManager).setEcgState(PpgUiState.Connected)
    }

    // =========================================================================
    // deviceDisconnected
    // =========================================================================

    @Test
    fun `deviceDisconnected sets isPolarDeviceConnected to false`() {
        activity.isPolarDeviceConnected = true
        callbacks.deviceDisconnected(fakeDeviceInfo)
        assertFalse(activity.isPolarDeviceConnected)
    }

    @Test
    fun `deviceDisconnected posts to runOnUiThread`() {
        callbacks.deviceDisconnected(fakeDeviceInfo)
        verify(activity).runOnUiThread(any(Runnable::class.java))
    }

    @Test
    fun `deviceDisconnected sets ECG UI state to Disconnected`() {
        callbacks.deviceDisconnected(fakeDeviceInfo)
        verify(uiStateManager).setEcgState(PpgUiState.Disconnected)
    }

    // =========================================================================
    // batteryLevelReceived
    // =========================================================================

    @Test
    fun `batteryLevelReceived stores battery as string`() {
        callbacks.batteryLevelReceived("ABCD1234", 82)
        assertEquals("82", activity.deviceBatteryLevel)
    }

    @Test
    fun `batteryLevelReceived stores 0 battery correctly`() {
        callbacks.batteryLevelReceived("ABCD1234", 0)
        assertEquals("0", activity.deviceBatteryLevel)
    }

    @Test
    fun `batteryLevelReceived stores 100 battery correctly`() {
        callbacks.batteryLevelReceived("ABCD1234", 100)
        assertEquals("100", activity.deviceBatteryLevel)
    }

    // =========================================================================
    // disInformationReceived
    // =========================================================================

    @Test
    fun `disInformationReceived stores firmware when UUID matches`() {
        callbacks.disInformationReceived(
            "ABCD1234",
            PolarCallbacks.FIRMWARE_VERSION_UUID,
            "2.1.1"
        )
        assertEquals("2.1.1", activity.deviceFirmware)
    }

    @Test
    fun `disInformationReceived ignores other UUIDs`() {
        activity.deviceFirmware = "NA"
        val otherUuid = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb") // manufacturer
        callbacks.disInformationReceived("ABCD1234", otherUuid, "Polar Electro")
        assertEquals("NA", activity.deviceFirmware)  // unchanged
    }

    @Test
    fun `FIRMWARE_VERSION_UUID is the DIS 0x2A26 characteristic`() {
        assertEquals(
            "00002a26-0000-1000-8000-00805f9b34fb",
            PolarCallbacks.FIRMWARE_VERSION_UUID.toString()
        )
    }

    // =========================================================================
    // hrNotificationReceived — thread-safety (Bug #2)
    // =========================================================================

    @Test
    fun `hrNotificationReceived does nothing when isEcgRunning is false`() {
        activity.isEcgRunning = false
        callbacks.hrNotificationReceived("ABCD1234", fakeHrData)
        verify(hrPlotter, never()).addValues1(
            org.mockito.Mockito.anyDouble(),
            org.mockito.Mockito.anyDouble(),
            any(List::class.java)
        )
    }

    @Test
    fun `hrNotificationReceived posts to runOnUiThread when isEcgRunning is true`() {
        activity.isEcgRunning = true
        val textEcgHr = mock(android.widget.TextView::class.java)
        activity.textEcgHr = textEcgHr

        callbacks.hrNotificationReceived("ABCD1234", fakeHrData)

        // runOnUiThread must have been called (thread-safety proof)
        verify(activity).runOnUiThread(any(Runnable::class.java))
    }

    @Test
    fun `hrNotificationReceived updates textEcgHr with HR value`() {
        activity.isEcgRunning = true
        val textEcgHr = mock(android.widget.TextView::class.java)
        activity.textEcgHr = textEcgHr

        callbacks.hrNotificationReceived("ABCD1234", fakeHrData)

        verify(textEcgHr).text = "75"
    }

    @Test
    fun `hrNotificationReceived calls hrPlotter addValues1 with correct hr`() {
        activity.isEcgRunning = true
        val textEcgHr = mock(android.widget.TextView::class.java)
        activity.textEcgHr = textEcgHr

        callbacks.hrNotificationReceived("ABCD1234", fakeHrData)

        @Suppress("UNCHECKED_CAST")
        val hrCaptor = ArgumentCaptor.forClass(Double::class.java)
        verify(hrPlotter).addValues1(
            org.mockito.Mockito.anyDouble(),
            hrCaptor.capture(),
            any(List::class.java)
        )
        assertEquals(75.0, hrCaptor.value as Double, 0.001)
    }

    @Test
    fun `hrNotificationReceived passes rrsMs list to hrPlotter`() {
        activity.isEcgRunning = true
        val textEcgHr = mock(android.widget.TextView::class.java)
        activity.textEcgHr = textEcgHr

        callbacks.hrNotificationReceived("ABCD1234", fakeHrDataWithRr)

        @Suppress("UNCHECKED_CAST")
        val rrsCaptor = ArgumentCaptor.forClass(List::class.java)
        verify(hrPlotter).addValues1(
            org.mockito.Mockito.anyDouble(),
            org.mockito.Mockito.anyDouble(),
            rrsCaptor.capture() as List<Int>
        )
        // SDK computes rrsMs from raw rrs (1024/1024 * 1000 = 1000 ms each)
        val captured = rrsCaptor.value as List<*>
        assertEquals(2, captured.size)
        assertEquals(1000, captured[0])
        assertEquals(1000, captured[1])
    }

    // =========================================================================
    // FIRMWARE_VERSION_UUID constant correctness
    // =========================================================================

    @Test
    fun `FIRMWARE_VERSION_UUID round-trips correctly`() {
        val uuid = PolarCallbacks.FIRMWARE_VERSION_UUID
        assertEquals(uuid, UUID.fromString(uuid.toString()))
    }
}
