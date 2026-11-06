package com.spark.habittracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkDatabaseMigrationChainTest {

    @Test
    fun migrationChain_isContiguousFromVersionOneToEight() {
        val migrations = SparkDatabaseFactory.migrations.sortedBy { it.startVersion }

        assertEquals("Expected migrations 1→2 through 7→8", 7, migrations.size)
        assertEquals(1, migrations.first().startVersion)
        assertEquals(SparkDatabase.VERSION, migrations.last().endVersion)

        migrations.zipWithNext().forEach { (current, next) ->
            assertEquals(
                "Migration " + current.startVersion + "→" + current.endVersion +
                    " must connect to " + next.startVersion + "→" + next.endVersion,
                current.endVersion,
                next.startVersion
            )
        }
    }

    @Test
    fun migrationChain_hasNoDuplicateStartVersions() {
        val startVersions = SparkDatabaseFactory.migrations.map { it.startVersion }

        assertEquals(startVersions.size, startVersions.distinct().size)
        assertTrue(startVersions.all { it in 1..7 })
    }
}
