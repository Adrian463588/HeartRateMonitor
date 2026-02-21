package com.example.samplewearmobileapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground Service that keeps the BLE connection to Polar H10
 * and Wearable Data Layer listener alive during data collection.
 *
 * Uses FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE to comply with
 * Android 14+ foreground service type requirements.
 *
 * Acquires a partial wake lock to prevent CPU sleep during
 * long recording sessions in research protocols.
 */
class MobileService : Service() {
    companion object {
        private const val TAG = "MobileService"
        private const val CHANNEL_ID = "HeartMonitorService"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "HeartMonitor::RecordingWakeLock"

        @Volatile
        private var isRunning = false

        fun startService(context: Context, message: String) {
            val intent = Intent(context, MobileService::class.java).apply {
                putExtra("message", message)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MobileService::class.java)
            context.stopService(intent)
        }

        fun isServiceRunning(): Boolean = isRunning
    }

    /** Binder for Activity ↔ Service communication */
    inner class LocalBinder : Binder() {
        fun getService(): MobileService = this@MobileService
    }

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra("message") ?: "Recording..."
        createNotificationChannel()

        val notification = buildNotification(message)

        // Use ServiceCompat for backward-compatible foregroundServiceType
        // Combine CONNECTED_DEVICE (BLE) + HEALTH (sensor data) types
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        isRunning = true
        Log.i(TAG, "Service started: $message")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        releaseWakeLock()
        isRunning = false
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    /**
     * Updates the notification text while service is running.
     * Useful for showing elapsed time or connection status.
     */
    fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    // ===== Private Helpers =====

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("HeartRate Monitor — Recording")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Data Recording Service",
            NotificationManager.IMPORTANCE_LOW  // Silent, persistent — prevents user from blocking
        ).apply {
            description = "Keeps BLE and sensor data collection alive"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                acquire(4 * 60 * 60 * 1000L) // 4 hours max for long research sessions
            }
            Log.i(TAG, "Wake lock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }
}