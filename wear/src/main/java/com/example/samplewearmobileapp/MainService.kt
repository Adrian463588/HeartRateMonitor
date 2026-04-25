package com.example.samplewearmobileapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.startForegroundService

/**
 * Foreground service that keeps the wear app alive during sensor recording.
 *
 * **Why a companion-object boolean for [isRunning]?**
 * `ContextCompat.getSystemService(context, MainService::class.java)` always
 * returns `null` for custom [Service] subclasses — it only works for system
 * services (e.g. [NotificationManager]). The workaround is a `@Volatile`
 * flag set in [onStartCommand] / [onDestroy].
 *
 * **SOLID compliance:**
 * - SRP: only responsibilities are starting/stopping the foreground notification.
 * - Static helpers follow the same pattern as the mobile [MobileService].
 */
class MainService : Service() {

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private val channelId = "MainServiceChannel"

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        createNotificationChannel()

        val pendingIntent = Intent(this, MainActivity::class.java).let { notifyIntent ->
            PendingIntent.getActivity(
                this, 0, notifyIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_tracking))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Notification channel
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Wear Tracking Service",
            NotificationManager.IMPORTANCE_LOW  // LOW avoids sound on every update
        )
        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    // -------------------------------------------------------------------------
    // Companion — static helpers
    // -------------------------------------------------------------------------

    companion object {
        private const val NOTIFICATION_ID = 1

        /**
         * `true` while [MainService] is actively running.
         *
         * Updated atomically in [onStartCommand] and [onDestroy].
         * Volatile ensures visibility across threads without locking.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** Starts the foreground service from any [Context]. */
        fun startService(context: Context, message: String) {
            val intent = Intent(context, MainService::class.java).apply {
                putExtra("message", message)
            }
            startForegroundService(context, intent)
        }

        /** Stops the foreground service from any [Context]. */
        fun stopService(context: Context) {
            context.stopService(Intent(context, MainService::class.java))
        }
    }
}