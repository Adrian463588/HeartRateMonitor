package com.example.samplewearmobileapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps the app alive while recording
 * sensor data. Shows a persistent notification during recording.
 */
class MobileService : Service() {


    companion object {
        private const val CHANNEL_ID = "MobileService"

        /**
         * Tracks whether the service is currently running.
         * Set in [onStartCommand] and cleared in [onDestroy].
         */
        @Volatile
        private var isRunning = false

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
    }

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
            .setContentText("Recording...")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID, "Mobile Foreground Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        serviceChannel.description = "Description"
        serviceChannel.enableLights(true)
        serviceChannel.lightColor = Color.BLUE

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }
}