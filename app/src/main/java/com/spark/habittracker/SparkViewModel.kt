package com.spark.habittracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SparkLoadState<out T> {
    data object Loading : SparkLoadState<Nothing>
    data class Data<T>(val value: T) : SparkLoadState<T>
    data class Error(val error: SparkError) : SparkLoadState<Nothing>
}

/**
 * First MVVM vertical slice: task list observation and task mutations.
 * The UI collects immutable state; persistence and coroutine ownership stay here.
 */
class SparkViewModel(
    private val tasksRepository: TaskRepository
) : ViewModel() {
    private val _tasks = MutableStateFlow<SparkLoadState<List<TaskEntity>>>(SparkLoadState.Loading)
    val tasks: StateFlow<SparkLoadState<List<TaskEntity>>> = _tasks.asStateFlow()

    private val _errors = MutableSharedFlow<SparkError>(extraBufferCapacity = 1)
    val errors: SharedFlow<SparkError> = _errors.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                tasksRepository.observeActiveTasks().collect { _tasks.value = SparkLoadState.Data(it) }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _tasks.value = SparkLoadState.Error(SparkError.Storage)
                _errors.emit(SparkError.Storage)
            }
        }
    }

    suspend fun archive(taskId: Long): Boolean = mutate { tasksRepository.archive(taskId) }
    suspend fun deletePermanently(taskId: Long): Boolean = mutate { tasksRepository.deletePermanently(taskId) }
    suspend fun duplicate(task: TaskEntity): Boolean = mutate { tasksRepository.duplicate(task) }
    suspend fun restore(taskId: Long): Boolean = mutate { tasksRepository.restore(taskId) }

    private suspend fun mutate(block: suspend () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _errors.emit(
                when (error) {
                    is IllegalArgumentException -> SparkError.Validation
                    is SecurityException -> SparkError.Permission
                    is android.database.SQLException -> SparkError.Storage
                    else -> SparkError.Unexpected
                }
            )
            false
        }
    }

    class Factory(private val container: SparkAppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SparkViewModel::class.java))
            return SparkViewModel(container.taskRepository) as T
        }
    }
}
