package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.models.PpgData
import com.example.samplewearmobileapp.models.PpgType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PpgDataHandlerTest {

    @Mock
    private lateinit var mockPlotter: PpgPlotter

    private lateinit var handler: PpgDataHandler

    @Before
    fun setUp() {
        handler = PpgDataHandler()
    }

    // -------------------------------------------------------------------------
    // Helper: create a PpgData with the real (ppgValues, timestamps, ppgType, windowSize, size) constructor
    // -------------------------------------------------------------------------

    private fun makeData(values: IntArray, timestamps: LongArray, declaredSize: Int = values.size): PpgData =
        PpgData(
            ppgValues  = values,
            timestamps = timestamps,
            ppgType    = PpgType.PPG_GREEN,
            windowSize = values.size,
            size       = declaredSize
        )

    // -------------------------------------------------------------------------
    // Null plotter
    // -------------------------------------------------------------------------

    @Test
    fun `process with null plotter returns 0 and does not crash`() {
        val data = makeData(intArrayOf(100, 200), longArrayOf(1L, 2L))
        val result = handler.process(PpgType.PPG_GREEN, data, null)
        assert(result == 0) { "Expected 0 but was $result" }
    }

    // -------------------------------------------------------------------------
    // Empty batch — size == 0
    // -------------------------------------------------------------------------

    @Test
    fun `process with empty batch returns 0`() {
        val data = makeData(intArrayOf(), longArrayOf(), 0)
        val result = handler.process(PpgType.PPG_GREEN, data, mockPlotter)
        assert(result == 0) { "Expected 0 but was $result" }
        verify(mockPlotter, never()).addValues(anyInt(), anyLong())
    }

    // -------------------------------------------------------------------------
    // Valid batch
    // -------------------------------------------------------------------------

    @Test
    fun `process with valid batch calls addValues for each sample`() {
        val data = makeData(intArrayOf(10, 20, 30), longArrayOf(1L, 2L, 3L))
        val result = handler.process(PpgType.PPG_IR, data, mockPlotter)
        assert(result == 3) { "Expected 3 but was $result" }
        verify(mockPlotter).addValues(10, 1L)
        verify(mockPlotter).addValues(20, 2L)
        verify(mockPlotter).addValues(30, 3L)
    }

    // -------------------------------------------------------------------------
    // Size mismatch guard — declared size > actual array length
    // -------------------------------------------------------------------------

    @Test
    fun `process with size mismatch returns -1 and does not call addValues`() {
        // ppgValues has 2 elements but declared size=3 → mismatch
        val data = makeData(intArrayOf(1, 2), longArrayOf(1L, 2L), declaredSize = 3)
        val result = handler.process(PpgType.PPG_RED, data, mockPlotter)
        assert(result == -1) { "Expected -1 but was $result" }
        verify(mockPlotter, never()).addValues(anyInt(), anyLong())
    }

    // -------------------------------------------------------------------------
    // PPG_RED channel
    // -------------------------------------------------------------------------

    @Test
    fun `process handles PPG_RED type correctly`() {
        val data = PpgData(
            ppgValues  = intArrayOf(50),
            timestamps = longArrayOf(9L),
            ppgType    = PpgType.PPG_RED,
            windowSize = 1,
            size       = 1
        )
        val result = handler.process(PpgType.PPG_RED, data, mockPlotter)
        assert(result == 1) { "Expected 1 but was $result" }
        verify(mockPlotter).addValues(50, 9L)
    }

    // -------------------------------------------------------------------------
    // Return value equals data.size for full batch
    // -------------------------------------------------------------------------

    @Test
    fun `process returns exact sample count for full window`() {
        val data = makeData(intArrayOf(1, 2, 3, 4, 5), longArrayOf(10L, 20L, 30L, 40L, 50L))
        val result = handler.process(PpgType.PPG_GREEN, data, mockPlotter)
        assert(result == 5) { "Expected 5 but was $result" }
    }
}
