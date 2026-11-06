package com.spark.habittracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * User-requested alarm-style habit reminder. The foreground service keeps the
 * ringtone alive until the user explicitly stops it. It never starts from an
 * external broadcast; only the app's own reminder receiver can start it.
 */
class AlarmRingingService : Service() {
    private var ringtone: Ringtone? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging()
            return START_NOT_STICKY
        }

        createChannel()
        val stopIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            Intent(this, AlarmRingingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.spark_logo)
            .setContentTitle("یادآوری عادت SPARK")
            .setContentText("زمان انجام عادت رسیده است.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "توقف زنگ", stopIntent)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        if (ringtone?.isPlaying != true) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = alarmUri?.let { RingtoneManager.getRingtone(this, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone?.isLooping = true
            ringtone?.play()
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "زنگ هشدار عادت‌ها", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        ringtone?.stop()
        ringtone = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.spark.habittracker.STOP_ALARM_RINGING"
        private const val CHANNEL_ID = "spark_alarm_ringing"
        private const val NOTIFICATION_ID = 94001
    }
}
