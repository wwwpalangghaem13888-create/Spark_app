package com.spark.habittracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId


internal fun parseOptionalHabitReminderTime(value: String): LocalTime? =
    value.trim().takeIf { it.isNotEmpty() }?.let(LocalTime::parse)

internal fun nextHabitReminder(habit: HabitEntity, afterMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
    if (!habit.isEnabled) return null
    val time = runCatching { parseOptionalHabitReminderTime(habit.reminderTime) }.getOrNull() ?: return null
    val after = Instant.ofEpochMilli(afterMillis).atZone(zoneId)
    val scheduled = habit.scheduledDays.split(",").map { it.trim() }.toSet()
    for (offset in 0L..7L) {
        val date = after.toLocalDate().plusDays(offset)
        if (date.dayOfWeek.name !in scheduled) continue
        val candidate = date.atTime(time).atZone(zoneId)
        if (candidate.isAfter(after)) return candidate.toInstant().toEpochMilli()
    }
    return null
}

object ReminderScheduler {
    const val ACTION_FIRE = "com.spark.habittracker.REMINDER_FIRE"
    const val ACTION_SNOOZE = "com.spark.habittracker.REMINDER_SNOOZE"
    private const val CHANNEL_ID = "spark_reminders"

    fun schedule(context: Context, reminder: ReminderEntity) {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra("reminder_id", reminder.id)
            .putExtra("owner_type", reminder.ownerType)
            .putExtra("owner_id", reminder.ownerId)
        val pending = PendingIntent.getBroadcast(
            context, reminder.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = reminder.snoozedUntil ?: reminder.triggerAt
        if (reminder.mode == "ALARM" &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms())
        ) {
            val showIntent = PendingIntent.getActivity(
                context,
                900000 + reminder.id.toInt(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarm.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
        val pending = PendingIntent.getBroadcast(
            context, reminderId.toInt(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        pending.cancel()
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "یادآورهای Spark", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    internal fun show(context: Context, reminderId: Long, ownerType: String) {
        ensureChannel(context)
        if ((Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) return
        val openIntent = PendingIntent.getActivity(
            context, 900000 + reminderId.toInt(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context, reminderId.toInt(),
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZE).putExtra("reminder_id", reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.spark_logo)
            .setContentTitle("یادآوری Spark")
            .setContentText(if (ownerType == "TASK") "زمان انجام وظیفه‌ات رسیده است." else "یادآوری برنامه‌ریزی‌شده")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .addAction(0, "تعویق ۱۰ دقیقه", snoozeIntent)
            .build()
        NotificationManagerCompat.from(context).notify(reminderId.toInt(), notification)
    }
}

internal suspend fun cancelAndDisableReminders(
    context: Context,
    db: SparkDatabase,
    ownerType: String,
    ownerId: Long
) {
    val dao = db.productivityDao()
    dao.getRemindersForOwner(ownerType, ownerId).forEach { ReminderScheduler.cancel(context, it.id) }
    dao.disableRemindersForOwner(ownerType, ownerId)
}

class AlarmRingingService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val language = getSharedPreferences("spark_locale", Context.MODE_PRIVATE)
            .getString("language", "fa") ?: "fa"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(ALARM_CHANNEL_ID, localizedText(language, "زنگ‌های Spark", "Spark alarms", "منبّهات Spark"), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val stopIntent = Intent(this, AlarmStopReceiver::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, STOP_REQUEST_CODE, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.spark_logo)
            .setContentTitle(localizedText(language, "زنگ هشدار Spark", "Spark alarm", "منبّه Spark"))
            .setContentText(localizedText(language, "برای توقف، دکمه زیر را بزن.", "Tap Stop to silence this alarm.", "اضغط إيقاف لإسكات المنبّه."))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, localizedText(language, "توقف", "Stop", "إيقاف"), stopPendingIntent)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        mediaPlayer?.release()
        mediaPlayer = null
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        if (alarmUri != null) {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(applicationContext, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        }
        if (wakeLock?.isHeld != true) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:SparkAlarm").apply {
                    acquire(10 * 60 * 1000L)
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.spark.habittracker.ALARM_STOP"
        private const val ALARM_CHANNEL_ID = "spark_alarm_ringing"
        private const val NOTIFICATION_ID = 74051
        private const val STOP_REQUEST_CODE = 74052
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1L)
        if (reminderId <= 0L) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = Room.databaseBuilder(context.applicationContext, SparkDatabase::class.java, "spark_tracker.db")
                    .addMigrations(SparkDatabase.MIGRATION_1_2, SparkDatabase.MIGRATION_2_3,
                        SparkDatabase.MIGRATION_3_4, SparkDatabase.MIGRATION_4_5, SparkDatabase.MIGRATION_5_6,
                        SparkDatabase.MIGRATION_6_7).build()
                try {
                    val dao = db.productivityDao()
                    if (intent.action == ReminderScheduler.ACTION_SNOOZE) {
                        val until = System.currentTimeMillis() + 10 * 60 * 1000L
                        dao.snoozeReminder(reminderId, until)
                        dao.getEnabledReminders().firstOrNull { it.id == reminderId }?.let { ReminderScheduler.schedule(context, it) }
                    } else {
                        val reminder = dao.getEnabledReminders().firstOrNull { it.id == reminderId }
                        if (reminder != null) {
                            if (reminder.mode == "ALARM") {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, AlarmRingingService::class.java)
                                        .putExtra("reminder_id", reminder.id)
                                )
                            } else {
                                ReminderScheduler.show(context, reminder.id, reminder.ownerType)
                            }
                            if (reminder.ownerType == "HABIT") {
                                val habit = db.habitDao().findHabit(reminder.ownerId)
                                val next = habit?.let { nextHabitReminder(it, System.currentTimeMillis() + 1000) }
                                if (next != null) {
                                    dao.updateReminderTrigger(reminder.id, next)
                                    ReminderScheduler.schedule(context, reminder.copy(triggerAt = next, snoozedUntil = null))
                                } else {
                                    dao.setReminderEnabled(reminder.id, false)
                                }
                            } else {
                                if (reminder.ownerType == "FOCUS") {
                                    val session = dao.findActiveFocusSessionById(reminder.ownerId)
                                    if (session != null && session.mode != "STOPWATCH" && session.pausedAt == null) {
                                        val now = System.currentTimeMillis()
                                        val elapsed = focusElapsedSeconds(session, now)
                                        val remaining = (session.plannedDurationSeconds - elapsed).coerceAtLeast(0)
                                        if (remaining == 0L) {
                                            dao.finishFocusSession(
                                                session.id,
                                                elapsed.coerceAtMost(session.plannedDurationSeconds),
                                                true,
                                                now
                                            )
                                            dao.setReminderEnabled(reminder.id, false)
                                        } else {
                                            val nextTrigger = now + remaining * 1000L
                                            dao.updateReminderTrigger(reminder.id, nextTrigger)
                                            ReminderScheduler.schedule(
                                                context,
                                                reminder.copy(triggerAt = nextTrigger, snoozedUntil = null)
                                            )
                                        }
                                    } else {
                                        dao.setReminderEnabled(reminder.id, false)
                                    }
                                } else {
                                    dao.setReminderEnabled(reminder.id, false)
                                }
                            }
                        }
                    }
                } finally {
                    db.close()
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmRingingService.ACTION_STOP) {
            context.stopService(Intent(context, AlarmRingingService::class.java))
        }
    }
}

class SparkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = Room.databaseBuilder(context.applicationContext, SparkDatabase::class.java, "spark_tracker.db")
                    .addMigrations(SparkDatabase.MIGRATION_1_2, SparkDatabase.MIGRATION_2_3,
                        SparkDatabase.MIGRATION_3_4, SparkDatabase.MIGRATION_4_5, SparkDatabase.MIGRATION_5_6,
                        SparkDatabase.MIGRATION_6_7).build()
                try {
                    val dao = db.productivityDao()
                    val rebuildHabitTimes = action == Intent.ACTION_TIME_CHANGED ||
                        action == Intent.ACTION_TIMEZONE_CHANGED
                    dao.getEnabledReminders().forEach { reminder ->
                        var scheduledReminder = reminder
                        if (rebuildHabitTimes && reminder.ownerType == "HABIT") {
                            val habit = db.habitDao().findHabit(reminder.ownerId)
                            val next = habit?.let { nextHabitReminder(it, System.currentTimeMillis() - 1L) }
                            if (next == null) {
                                dao.setReminderEnabled(reminder.id, false)
                                ReminderScheduler.cancel(context, reminder.id)
                                return@forEach
                            }
                            dao.updateReminderTrigger(reminder.id, next)
                            scheduledReminder = reminder.copy(triggerAt = next, snoozedUntil = null)
                        }
                        ReminderScheduler.cancel(context, reminder.id)
                        ReminderScheduler.schedule(context, scheduledReminder)
                    }
                } finally {
                    db.close()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
