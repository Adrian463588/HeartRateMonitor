package com.example.samplewearmobileapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class required by Hilt for dependency injection.
 * Declared in AndroidManifest.xml via android:name=".HeartMonitorApp".
 */
@HiltAndroidApp
class HeartMonitorApp : Application()
