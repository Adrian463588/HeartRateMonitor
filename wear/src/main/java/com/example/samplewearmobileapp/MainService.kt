package com.example.samplewearmobileapp

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Keeps the sensor process/CPU available; ownership ends on explicit stop or Activity destruction. */
class MainService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "MainService"

        fun startService(context: Context, message: String) {
            ContextCompat.startForegroundService(context,
                Intent(context, MainService::class.java).putExtra("message", message))
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, MainService::class.java))
        }
    }

    // Session-scoped: release on Pause and onDestroy; a timeout would truncate an active recording.
    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra("message") ?: "Recording sensors"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Watch recording", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Watch sensor recording")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setOngoing(true).build())
        if (message == "Paused") {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } else {
            if (wakeLock == null) wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartRateMonitor:WatchRecording")
            if (wakeLock?.isHeld != true) wakeLock?.acquire()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
