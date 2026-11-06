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

internal fun Throwable.toSparkError(): SparkError = when (this) {
    is IllegalArgumentException -> SparkError.Validation
    is NoSuchElementException -> SparkError.NotFound
    is SecurityException -> SparkError.Permission
    is android.database.SQLException -> SparkError.Storage
    else -> SparkError.Unexpected
}

internal inline fun <T> sparkResult(block: () -> T): SparkResult<T> = try {
    SparkResult.Success(block())
} catch (error: Throwable) {
    SparkResult.Failure(error.toSparkError(), error)
}

fun SparkError.userMessage(): String = when (this) {
    SparkError.Validation -> "اطلاعات واردشده معتبر نیست."
    SparkError.NotFound -> "مورد درخواستی پیدا نشد."
    SparkError.Storage -> "ذخیره‌سازی اطلاعات با مشکل مواجه شد."
    SparkError.Permission -> "مجوز لازم در دسترس نیست."
    SparkError.Unexpected -> "خطای غیرمنتظره‌ای رخ داد."
}
