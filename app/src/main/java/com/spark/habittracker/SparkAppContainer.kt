package com.spark.habittracker

import android.content.Context

/**
 * Manual dependency-injection composition root.
 *
 * A small application does not need a runtime DI framework yet; this container
 * keeps construction centralized while leaving a clean migration path to Hilt
 * if the graph grows enough to justify it.
 */
class SparkAppContainer(context: Context) {
    val database: SparkDatabase = SparkDatabaseFactory.create(context.applicationContext)
    val taskRepository: TaskRepository = TaskRepository(database.taskDao())
    val habitRepository: HabitRepository = HabitRepository(database.habitDao())
    val productivityRepository: ProductivityRepository = ProductivityRepository(database.productivityDao())
}

class SparkApplication : android.app.Application() {
    lateinit var container: SparkAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = SparkAppContainer(this)
    }
}
