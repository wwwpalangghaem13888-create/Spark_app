package com.spark.habittracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SparkArchitectureTest {
    @Test
    fun everyBottomNavigationIndexMapsToItsTypedDestination() {
        SparkDestination.values().forEach { destination ->
            assertSame(destination, SparkDestination.fromTab(destination.tabIndex))
            assertSame(destination, SparkDestination.fromRoute(destination.route))
        }
    }

    @Test
    fun unknownNavigationInputsAreHandledSafely() {
        assertSame(SparkDestination.Backup, SparkDestination.fromTab(-1))
        assertNull(SparkDestination.fromRoute("missing"))
    }

    @Test
    fun errorsExposeStablePersianMessages() {
        assertEquals("اطلاعات واردشده معتبر نیست.", SparkError.Validation.userMessage())
        assertEquals("ذخیره‌سازی اطلاعات با مشکل مواجه شد.", SparkError.Storage.userMessage())
        assertEquals("مجوز لازم در دسترس نیست.", SparkError.Permission.userMessage())
    }
}
