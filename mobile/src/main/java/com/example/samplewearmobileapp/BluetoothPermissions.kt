package com.example.samplewearmobileapp

import android.Manifest
import android.os.Build

internal fun requiredBluetoothPermissions(apiLevel: Int): Array<String> =
    if (apiLevel >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
