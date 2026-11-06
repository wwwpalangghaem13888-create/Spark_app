package com.spark.habittracker

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration

/**
 * Single source of truth for opening Spark's Room database.
 *
 * Short-lived callers (receivers and widgets) should close the returned instance
 * in a finally block. The activity may retain its instance for the process life.
 */
internal object SparkDatabaseFactory {
    private const val DATABASE_NAME = "spark_tracker.db"

    /**
     * Keep migration registration identical for the app, receivers and widgets.
     */
    internal val migrations: Array<Migration> = arrayOf(
        SparkDatabase.MIGRATION_1_2,
        SparkDatabase.MIGRATION_2_3,
        SparkDatabase.MIGRATION_3_4,
        SparkDatabase.MIGRATION_4_5,
        SparkDatabase.MIGRATION_5_6,
        SparkDatabase.MIGRATION_6_7,
        SparkDatabase.MIGRATION_7_8,
        SparkDatabase.MIGRATION_8_9
    )

    fun create(context: Context): SparkDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            SparkDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(*migrations)
            .build()
}
