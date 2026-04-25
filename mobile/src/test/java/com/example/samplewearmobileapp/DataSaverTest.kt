package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.models.EcgPlotArrays
import com.example.samplewearmobileapp.models.PpgPlotArrays
import com.example.samplewearmobileapp.models.PpgType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Date

/**
 * Unit tests for [DataSaver.SessionMetadata], [DataSaver.SaveResult],
 * [EcgPlotArrays], and [PpgPlotArrays].
 *
 * Full SAF / coroutine paths (saveEcgData, savePpgData) require a real ContentResolver
 * and a real Storage Access Framework tree URI — these are covered by instrumented tests.
 *
 * Here we test the pure-Kotlin, framework-free logic:
 *  1. [DataSaver.SessionMetadata] computed properties (durationMs, durationSec)
 *  2. [DataSaver.SaveResult] sealed class structure and data class equality
 *  3. [EcgPlotArrays] model construction and equality
 *  4. [PpgPlotArrays] model construction
 *  5. [PpgType] enum stability (3 values, stable names)
 */
@RunWith(JUnit4::class)
class DataSaverTest {

    private val validMeta = DataSaver.SessionMetadata(
        stopTime           = Date(2000L),
        startTime          = Date(0L),
        deviceId           = "1A2B3C",
        deviceName         = "PolarH10",
        deviceFirmware     = "v3.0",
        deviceBatteryLevel = "85",
        deviceStopHr       = "72",
        calculatedStopHr   = "73",
        peakCount          = 42,
        appVersion         = "1.1"
    )

    // -------------------------------------------------------------------------
    // SessionMetadata computed properties
    // -------------------------------------------------------------------------

    @Test
    fun `SessionMetadata durationMs equals stopTime minus startTime`() {
        assertEquals(2000L, validMeta.durationMs)
    }

    @Test
    fun `SessionMetadata durationSec is durationMs divided by 1000`() {
        assertEquals(2.0, validMeta.durationSec, 0.001)
    }

    @Test
    fun `SessionMetadata zero duration is handled`() {
        val zeroDuration = validMeta.copy(startTime = validMeta.stopTime)
        assertEquals(0L, zeroDuration.durationMs)
        assertEquals(0.0, zeroDuration.durationSec, 0.0)
    }

    @Test
    fun `SessionMetadata fields are stored correctly`() {
        assertEquals("1A2B3C",   validMeta.deviceId)
        assertEquals("PolarH10", validMeta.deviceName)
        assertEquals("v3.0",     validMeta.deviceFirmware)
        assertEquals("85",       validMeta.deviceBatteryLevel)
        assertEquals(42,          validMeta.peakCount)
        assertEquals("1.1",      validMeta.appVersion)
    }

    // -------------------------------------------------------------------------
    // EcgPlotArrays model
    // -------------------------------------------------------------------------

    @Test
    fun `EcgPlotArrays can be constructed`() {
        val arrays = EcgPlotArrays(
            ecg       = doubleArrayOf(1.0, 2.0, 3.0),
            peaks     = booleanArrayOf(false, true, false),
            timestamp = longArrayOf(100L, 200L, 300L)
        )
        assertNotNull(arrays)
        assertEquals(3, arrays.ecg.size)
        assertTrue(arrays.peaks[1])
        assertEquals(200L, arrays.timestamp[1])
    }

    @Test
    fun `EcgPlotArrays equality is value-based not reference-based`() {
        val a = EcgPlotArrays(doubleArrayOf(1.0), booleanArrayOf(false), longArrayOf(1L))
        val b = EcgPlotArrays(doubleArrayOf(1.0), booleanArrayOf(false), longArrayOf(1L))
        assertEquals(a, b)
    }

    @Test
    fun `EcgPlotArrays with different ecg values are not equal`() {
        val a = EcgPlotArrays(doubleArrayOf(1.0), booleanArrayOf(false), longArrayOf(1L))
        val b = EcgPlotArrays(doubleArrayOf(9.0), booleanArrayOf(false), longArrayOf(1L))
        assertTrue(a != b)
    }

    // -------------------------------------------------------------------------
    // PpgPlotArrays model
    // -------------------------------------------------------------------------

    @Test
    fun `PpgPlotArrays stores values correctly`() {
        val arrays = PpgPlotArrays(intArrayOf(500, 600, 700), longArrayOf(1L, 2L, 3L))
        assertEquals(500, arrays.ppg[0])
        assertEquals(3L,  arrays.timestamp[2])
    }

    @Test
    fun `PpgPlotArrays equality is value-based`() {
        val a = PpgPlotArrays(intArrayOf(100), longArrayOf(1L))
        val b = PpgPlotArrays(intArrayOf(100), longArrayOf(1L))
        assertEquals(a, b)
    }

    // -------------------------------------------------------------------------
    // PpgType enum
    // -------------------------------------------------------------------------

    @Test
    fun `PpgType has exactly three values`() {
        assertEquals(3, PpgType.values().size)
    }

    @Test
    fun `PpgType enum names are stable`() {
        assertEquals("PPG_GREEN", PpgType.PPG_GREEN.name)
        assertEquals("PPG_IR",    PpgType.PPG_IR.name)
        assertEquals("PPG_RED",   PpgType.PPG_RED.name)
    }

    // -------------------------------------------------------------------------
    // SaveResult sealed class
    // -------------------------------------------------------------------------

    @Test
    fun `SaveResult Success stores fileName`() {
        val result = DataSaver.SaveResult.Success("session1_ECG_2024-01-01_12-00.csv")
        assertEquals("session1_ECG_2024-01-01_12-00.csv", result.fileName)
    }

    @Test
    fun `SaveResult Success data class equality is value-based`() {
        val a = DataSaver.SaveResult.Success("file.csv")
        val b = DataSaver.SaveResult.Success("file.csv")
        assertEquals(a, b)
    }

    @Test
    fun `SaveResult Success instances with different filenames are not equal`() {
        val a = DataSaver.SaveResult.Success("file_a.csv")
        val b = DataSaver.SaveResult.Success("file_b.csv")
        assertTrue(a != b)
    }

    @Test
    fun `SaveResult Failure stores fileName and null cause`() {
        val result = DataSaver.SaveResult.Failure("session1-ECG")
        assertEquals("session1-ECG", result.fileName)
        assertNull(result.cause)
    }

    @Test
    fun `SaveResult Failure stores non-null cause`() {
        val ex = IllegalStateException("disk full")
        val result = DataSaver.SaveResult.Failure("session1-ECG", ex)
        assertEquals("disk full", result.cause?.message)
    }

    @Test
    fun `SaveResult Failure data class equality is value-based`() {
        val a = DataSaver.SaveResult.Failure("file.csv")
        val b = DataSaver.SaveResult.Failure("file.csv")
        assertEquals(a, b)
    }

    @Test
    fun `SaveResult Success is not Failure`() {
        val success: DataSaver.SaveResult = DataSaver.SaveResult.Success("f.csv")
        assertTrue(success is DataSaver.SaveResult.Success)
        assertTrue(success !is DataSaver.SaveResult.Failure)
    }

    @Test
    fun `SaveResult Failure is not Success`() {
        val failure: DataSaver.SaveResult = DataSaver.SaveResult.Failure("f.csv")
        assertTrue(failure is DataSaver.SaveResult.Failure)
        assertTrue(failure !is DataSaver.SaveResult.Success)
    }

    @Test
    fun `SaveResult sealed class when expression is exhaustive for all subtypes`() {
        // Kotlin enforces exhaustive when() at compile time for sealed classes.
        // If a third subtype is added, this function will fail to compile,
        // catching the omission before any runtime breakage.
        fun classify(result: DataSaver.SaveResult): String = when (result) {
            is DataSaver.SaveResult.Success -> "success"
            is DataSaver.SaveResult.Failure -> "failure"
            // No else branch — compile error if a new subtype is added without handling it
        }
        assertEquals("success", classify(DataSaver.SaveResult.Success("f.csv")))
        assertEquals("failure", classify(DataSaver.SaveResult.Failure("f.csv")))
    }
}
