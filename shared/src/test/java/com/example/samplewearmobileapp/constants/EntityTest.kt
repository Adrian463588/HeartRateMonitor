package com.example.samplewearmobileapp.constants

import org.junit.Assert.*
import org.junit.Test

class EntityTest {

    @Test
    fun `PHONE_APP constant is mobile`() {
        assertEquals("mobile", Entity.PHONE_APP)
    }

    @Test
    fun `WEAR_APP constant is wear`() {
        assertEquals("wear", Entity.WEAR_APP)
    }

    @Test
    fun `constants are distinct`() {
        assertNotEquals(Entity.PHONE_APP, Entity.WEAR_APP)
    }
}
