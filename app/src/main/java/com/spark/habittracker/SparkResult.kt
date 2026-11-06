package com.spark.habittracker

/**
 * Stable error vocabulary shared by repositories and presentation state.
 * Raw exception messages are intentionally not exposed to end users.
 */
sealed interface SparkError {
    data object Validation : SparkError
    data object NotFound : SparkError
    data object Storage : SparkError
    data object Permission : SparkError
    data object Unexpected : SparkError
}

sealed interface SparkResult<out T> {
    data class Success<T>(val value: T) : SparkResult<T>
    data class Failure(val error: SparkError, val cause: Throwable? = null) : SparkResult<Nothing>
}

internal inline fun <T> sparkResult(block: () -> T): SparkResult<T> = try {
    SparkResult.Success(block())
} catch (error: IllegalArgumentException) {
    SparkResult.Failure(SparkError.Validation, error)
} catch (error: SecurityException) {
    SparkResult.Failure(SparkError.Permission, error)
} catch (error: android.database.SQLException) {
    SparkResult.Failure(SparkError.Storage, error)
} catch (error: Throwable) {
    SparkResult.Failure(SparkError.Unexpected, error)
}

fun SparkError.userMessage(): String = when (this) {
    SparkError.Validation -> "اطلاعات واردشده معتبر نیست."
    SparkError.NotFound -> "مورد درخواستی پیدا نشد."
    SparkError.Storage -> "ذخیره‌سازی اطلاعات با مشکل مواجه شد."
    SparkError.Permission -> "مجوز لازم در دسترس نیست."
    SparkError.Unexpected -> "خطای غیرمنتظره‌ای رخ داد."
}
