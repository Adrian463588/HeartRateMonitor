package com.example.samplewearmobileapp

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps the app alive while recording
 * sensor data. Shows a persistent notification during recording.
 */
class MobileService : Service() {
    private val CHANNEL_ID = "MobileService"

    companion object {
        /**
         * Tracks whether the service is currently running.
         * Set in [onStartCommand] and cleared in [onDestroy].
         */
        @Volatile
        private var isRunning = false
        private var wakeLock: PowerManager.WakeLock? = null

        fun startService(context: Context, message: String) {
            val intent = Intent(context, MobileService::class.java)
            intent.putExtra("message", message)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MobileService::class.java)
            context.stopService(intent)
        }

        fun isServiceRunning(): Boolean = isRunning
        internal fun isRecordingCpuAwake(): Boolean = wakeLock?.isHeld == true
    }

    // Recording-scoped: the Watch lease needs heartbeats even when the phone screen is off.
    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra("message")
        createNotificationChannel()

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE)
            }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Main Foreground Service Sample App")
            .setContentText(message ?: "Recording...")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
        if (message == "Paused") {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } else {
            if (wakeLock == null) wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartRateMonitor:PhoneRecording")
            if (wakeLock?.isHeld != true) wakeLock?.acquire()
        }
        isRunning = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        super.onDestroy()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID, "Mobile Foreground Service Channel",
            NotificationManager.IMPORTANCE_HIGH
        )
        serviceChannel.description = "Description"
        serviceChannel.enableLights(true)
        serviceChannel.lightColor = Color.BLUE

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }
}
