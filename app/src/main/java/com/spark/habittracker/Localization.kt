package com.spark.habittracker

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.core.app.NotificationManagerCompat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun localizedText(language: String, fa: String, en: String, ar: String): String =
    when (language.lowercase()) {
        "en" -> en
        "ar" -> ar
        else -> fa
    }

internal fun syncLanguagePreference(context: Context, language: String) {
    val normalized = language.takeIf { it in setOf("fa", "en", "ar") } ?: "fa"
    context.getSharedPreferences("spark_locale", Context.MODE_PRIVATE)
        .edit()
        .putString("language", normalized)
        .commit()
}

@Composable
internal fun ReminderModeSelector(
    mode: String,
    hasReminderTime: Boolean,
    language: String,
    onModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val notificationsAllowed = remember(context) {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        runtimePermissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val exactAlarmsAllowed = remember(context) {
        Build.VERSION.SDK_INT < 31 ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            localizedText(language, "نوع یادآوری", "Reminder type", "نوع التذكير"),
            color = if (hasReminderTime) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == "NOTIFICATION",
                enabled = hasReminderTime,
                onClick = { onModeChange("NOTIFICATION") },
                label = { Text(localizedText(language, "اعلان", "Notification", "إشعار")) }
            )
            FilterChip(
                selected = mode == "ALARM",
                enabled = hasReminderTime,
                onClick = { onModeChange("ALARM") },
                label = { Text(localizedText(language, "زنگ هشدار", "Phone alarm", "منبّه")) }
            )
        }
        if (!notificationsAllowed) {
            Text(
                localizedText(
                    language,
                    "اعلان‌های Spark در تنظیمات اندروید مسدود شده‌اند؛ اعلان عادی و دکمه توقف زنگ ممکن است نمایش داده نشوند.",
                    "Spark notifications are blocked in Android settings. Notifications and the alarm stop action may not be visible.",
                    "إشعارات Spark محظورة في إعدادات Android؛ قد لا تظهر الإشعارات أو زر إيقاف المنبّه."
                ),
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (mode == "ALARM" && !exactAlarmsAllowed) {
            Text(
                localizedText(
                    language,
                    "دسترسی زمان‌بندی دقیق فعال نیست؛ زنگ ممکن است با تأخیر اجرا شود. دسترسی «هشدارها و یادآورها» را در تنظیمات برنامه فعال کن.",
                    "Exact alarm access is off. The alarm may be delayed; enable \"Alarms & reminders\" in Android app settings.",
                    "إذن التنبيهات الدقيقة غير مفعّل؛ قد يتأخر المنبّه. فعّل إذن «التنبيهات والتذكيرات» من إعدادات التطبيق."
                ),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            TextButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }) {
                Text(localizedText(language, "فعال‌سازی دسترسی زنگ دقیق", "Enable exact alarms", "تفعيل التنبيهات الدقيقة"))
            }
        }
    }
}
