package com.pearsonmedia.lastlogged.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pearsonmedia.lastlogged.data.local.entity.CompletionLog
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import com.pearsonmedia.lastlogged.data.repository.TrackerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UndoState(
    val logId: String,
    val trackerItemId: String,
    val previousCompletedAt: Long?
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TrackerRepository
) : ViewModel() {

    private val _undoState = MutableStateFlow<UndoState?>(null)
    val undoState: StateFlow<UndoState?> = _undoState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val trackerItems: StateFlow<List<TrackerItem>> = repository.getActiveItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<TrackerCategory>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val itemsByCategory: StateFlow<Map<TrackerCategory?, List<TrackerItem>>> =
        combine(trackerItems, categories) { items, cats ->
            val catMap = cats.associateBy { it.id }
            items.groupBy { item -> item.categoryId?.let { catMap[it] } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun logCompletion(item: TrackerItem) {
        viewModelScope.launch {
            try {
                val log = repository.logCompletion(item.id)
                _undoState.value = UndoState(
                    logId = log.id,
                    trackerItemId = item.id,
                    previousCompletedAt = item.lastCompletedAt
                )
                // Auto-dismiss undo after 4 seconds
                delay(4000)
                if (_undoState.value?.logId == log.id) {
                    _undoState.value = null
                }
            } catch (e: Exception) {
                _error.value = "Failed to log completion: ${e.message}"
            }
        }
    }

    fun undoLastLog() {
        val state = _undoState.value ?: return
        viewModelScope.launch {
            try {
                repository.undoLog(state.logId, state.trackerItemId, state.previousCompletedAt)
                _undoState.value = null
            } catch (e: Exception) {
                _error.value = "Failed to undo: ${e.message}"
            }
        }
    }

    fun archiveItem(item: TrackerItem) {
        viewModelScope.launch {
            try {
                repository.archiveItem(item.id)
            } catch (e: Exception) {
                _error.value = "Failed to archive: ${e.message}"
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
