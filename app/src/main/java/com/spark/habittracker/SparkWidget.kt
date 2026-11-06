package com.spark.habittracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class SparkWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { refresh(context, manager, it) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SparkWidgetProvider::class.java))
            ids.forEach { refresh(context, manager, it) }
        }

        private fun refresh(context: Context, manager: AppWidgetManager, widgetId: Int) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val db = Room.databaseBuilder(context.applicationContext, SparkDatabase::class.java, "spark_tracker.db")
                    .addMigrations(
                        SparkDatabase.MIGRATION_1_2,
                        SparkDatabase.MIGRATION_2_3,
                        SparkDatabase.MIGRATION_3_4,
                        SparkDatabase.MIGRATION_4_5,
                        SparkDatabase.MIGRATION_5_6
                    ).build()
                try {
                    val tasks = db.taskDao().getWidgetTasks()
                    val views = RemoteViews(context.packageName, R.layout.spark_widget)
                    views.setTextViewText(R.id.widget_title, "SPARK • وظایف امروز")
                    val rowIds = listOf(R.id.widget_task_1, R.id.widget_task_2, R.id.widget_task_3)
                    rowIds.forEachIndexed { index, viewId ->
                        val task = tasks.getOrNull(index)
                        if (task == null) {
                            views.setTextViewText(viewId, "وظیفه‌ای باقی نمانده")
                            views.setOnClickPendingIntent(viewId, PendingIntent.getActivity(context, 0,
                                Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                        } else {
                            val due = task.dueDate?.let { " • " + it }.orEmpty()
                            views.setTextViewText(viewId, "○ " + task.title + due)
                            val action = Intent(context, SparkWidgetActionReceiver::class.java)
                                .setAction("com.spark.habittracker.COMPLETE_TASK")
                                .putExtra("task_id", task.id)
                            val pending = PendingIntent.getBroadcast(context, task.id.toInt(), action,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            views.setOnClickPendingIntent(viewId, pending)
                        }
                    }
                    views.setOnClickPendingIntent(R.id.widget_open, PendingIntent.getActivity(context, 100,
                        Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    manager.updateAppWidget(widgetId, views)
                } finally {
                    db.close()
                }
            }
        }
    }
}

class SparkWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.spark.habittracker.COMPLETE_TASK") return
        val taskId = intent.getLongExtra("task_id", -1L)
        if (taskId <= 0L) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = Room.databaseBuilder(context.applicationContext, SparkDatabase::class.java, "spark_tracker.db")
                    .addMigrations(
                        SparkDatabase.MIGRATION_1_2,
                        SparkDatabase.MIGRATION_2_3,
                        SparkDatabase.MIGRATION_3_4,
                        SparkDatabase.MIGRATION_4_5,
                        SparkDatabase.MIGRATION_5_6
                    ).build()
                try {
                    db.productivityDao().recordTaskCompletion(taskId, true, System.currentTimeMillis(), LocalDate.now().toString())
                } finally {
                    db.close()
                }
                SparkWidgetProvider.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
