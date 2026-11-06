package com.spark.habittracker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(db: SparkDatabase, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val language = LocalSparkLanguage.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("پشتیبان شامل عادت‌ها، سوابق، وظایف، زیرکارها، اهداف، جلسات تمرکز و یادآورهاست.") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            status = try {
                SparkBackupManager.export(context, db, uri)
                "پشتیبان با موفقیت ذخیره شد."
            } catch (error: Exception) {
                "ذخیره پشتیبان ناموفق بود: " + (error.message ?: "خطای ناشناخته")
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            status = try {
                val csv = exportTasksCsv(db.taskDao().getAllTasks())
                val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open CSV destination.")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
                "خروجی CSV وظایف ذخیره شد."
            } catch (error: Exception) {
                "خروجی CSV ناموفق بود: " + (error.message ?: "خطای ناشناخته")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            status = try {
                val count = SparkBackupManager.import(context, db, uri)
                db.productivityDao().getEnabledReminders()
                    .forEach { reminder ->
                        val alarmStyle = reminder.ownerType == "HABIT" &&
                            db.habitDao().findHabit(reminder.ownerId)?.reminderMode == "ALARM"
                        ReminderScheduler.schedule(context, reminder, alarmStyle)
                    }
                SparkWidgetProvider.refreshAll(context)
                "بازیابی انجام شد. $count رکورد وارد یا به‌روزرسانی شد."
            } catch (error: Exception) {
                "فایل بازیابی نشد؛ داده‌های فعلی تغییر نکردند. " + (error.message ?: "فایل نامعتبر")
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(localizedText(language, "پشتیبان‌گیری و بازیابی", "Backup and restore", "النسخ الاحتياطي والاستعادة"), style = MaterialTheme.typography.headlineSmall)
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(localizedText(language, "پشتیبان محلی JSON", "Local JSON backup", "نسخة JSON محلية"), style = MaterialTheme.typography.titleMedium)
                Text("فایل را در فضای دلخواه ذخیره کن. Spark برای استفادهٔ اصلی به حساب کاربری یا اینترنت نیاز ندارد.")
                Button(onClick = { exportLauncher.launch("spark-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                    Text(localizedText(language, "ساخت پشتیبان", "Create backup", "إنشاء نسخة احتياطية"))
                }
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(localizedText(language, "خروجی CSV", "CSV export", "تصدير CSV"), style = MaterialTheme.typography.titleMedium)
                Text("خروجی جدولی وظایف برای استفاده در صفحه‌گسترده‌ها.")
                Button(onClick = { csvLauncher.launch("spark-tasks.csv") }, modifier = Modifier.fillMaxWidth()) {
                    Text(localizedText(language, "ذخیره CSV وظایف", "Export tasks CSV", "تصدير المهام CSV"))
                }
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(localizedText(language, "بازیابی پشتیبان", "Restore backup", "استعادة نسخة احتياطية"), style = MaterialTheme.typography.titleMedium)
                Text("بازیابی به‌صورت ادغام انجام می‌شود؛ رکوردهای فعلی پاک نمی‌شوند. نسخه و ارتباط شناسه‌ها پیش از اعمال بررسی می‌شود.")
                Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(localizedText(language, "انتخاب فایل پشتیبان", "Choose backup file", "اختر ملف النسخة الاحتياطية"))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(status, style = MaterialTheme.typography.bodySmall)
    }
}
