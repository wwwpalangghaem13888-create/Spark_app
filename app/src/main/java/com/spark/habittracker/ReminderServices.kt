package com.spark.habittracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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


internal fun nextHabitReminder(habit: HabitEntity, afterMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
    if (!habit.isEnabled) return null
    val time = runCatching { LocalTime.parse(habit.reminderTime) }.getOrNull() ?: return null
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
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.snoozedUntil ?: reminder.triggerAt, pending)
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
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
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

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1L)
        if (reminderId <= 0L) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = Room.databaseBuilder(context.applicationContext, SparkDatabase::class.java, "spark_tracker.db")
                    .addMigrations(SparkDatabase.MIGRATION_1_2, SparkDatabase.MIGRATION_2_3,
                        SparkDatabase.MIGRATION_3_4, SparkDatabase.MIGRATION_4_5, SparkDatabase.MIGRATION_5_6).build()
                try {
                    val dao = db.productivityDao()
                    if (intent.action == ReminderScheduler.ACTION_SNOOZE) {
                        val until = System.currentTimeMillis() + 10 * 60 * 1000L
                        dao.snoozeReminder(reminderId, until)
                        dao.getEnabledReminders().firstOrNull { it.id == reminderId }?.let { ReminderScheduler.schedule(context, it) }
                    } else {
                        val reminder = dao.getEnabledReminders().firstOrNull { it.id == reminderId }
                        if (reminder != null) {
                            ReminderScheduler.show(context, reminder.id, reminder.ownerType)
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

class SparkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = Room.databaseBuilder(context.applicationContext, SparkDatabase::class.java, "spark_tracker.db")
                    .addMigrations(SparkDatabase.MIGRATION_1_2, SparkDatabase.MIGRATION_2_3,
                        SparkDatabase.MIGRATION_3_4, SparkDatabase.MIGRATION_4_5, SparkDatabase.MIGRATION_5_6).build()
                try {
                    db.productivityDao().getEnabledReminders()
                        .forEach { ReminderScheduler.schedule(context, it) }
                } finally {
                    db.close()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
