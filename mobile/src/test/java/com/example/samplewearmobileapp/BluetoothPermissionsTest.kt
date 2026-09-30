package com.example.samplewearmobileapp

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BluetoothPermissionsTest {
    @Test
    fun `Android 11 requires location for Bluetooth scanning`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            requiredBluetoothPermissions(30)
        )
    }

    @Test
    fun `Android 12 and newer require nearby device permissions`() {
        val expected = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        assertArrayEquals(expected, requiredBluetoothPermissions(31))
        assertArrayEquals(expected, requiredBluetoothPermissions(34))
    }
}
