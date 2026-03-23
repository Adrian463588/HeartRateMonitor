package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Test

class CompiledDataTest {

    private fun makeHeartData() = HeartData(72, 800, "2024-01-01T00:00:00")
    private fun makePpgData() = PpgData(5, PpgType.PPG_GREEN)

    @Test
    fun `constructor sets fields correctly`() {
        val hd = makeHeartData()
        val ppg = makePpgData()
        val compiled = CompiledData(hd, ppg)
        assertEquals(hd, compiled.heartData)
        assertEquals(ppg, compiled.ppgData)
    }

    @Test
    fun `data class equality works`() {
        val a = CompiledData(makeHeartData(), makePpgData())
        val b = CompiledData(makeHeartData(), makePpgData())
        assertEquals(a, b)
    }

    @Test
    fun `data class detects different heartData`() {
        val a = CompiledData(HeartData(60, 1000, "ts"), makePpgData())
        val b = CompiledData(HeartData(80, 1000, "ts"), makePpgData())
        assertNotEquals(a, b)
    }

    @Test
    fun `copy preserves unchanged field`() {
        val original = CompiledData(makeHeartData(), makePpgData())
        val newHd = HeartData(90, 600, "ts2")
        val copy = original.copy(heartData = newHd)
        assertEquals(newHd, copy.heartData)
        assertEquals(original.ppgData, copy.ppgData)
    }

    @Test
    fun `fields are mutable`() {
        val compiled = CompiledData(makeHeartData(), makePpgData())
        val newHd = HeartData(100, 500, "updated")
        compiled.heartData = newHd
        assertEquals(newHd, compiled.heartData)
    }
}
