package com.pearsonmedia.lastlogged.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pearsonmedia.lastlogged.data.local.entity.CompletionLog
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import com.pearsonmedia.lastlogged.data.repository.TrackerRepository
import com.pearsonmedia.lastlogged.service.SuccessSoundService
import com.pearsonmedia.lastlogged.service.SupabaseService
import com.pearsonmedia.lastlogged.util.StreakUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UndoState(
    val logId: String,
    val trackerItemId: String,
    val previousCompletedAt: Long?
)

data class MilestoneEvent(
    val trackerName: String,
    val milestone: Int,
    val label: String
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TrackerRepository,
    private val supabaseService: SupabaseService,
    private val successSoundService: SuccessSoundService
) : ViewModel() {

    private val _undoState = MutableStateFlow<UndoState?>(null)
    val undoState: StateFlow<UndoState?> = _undoState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Fires exactly once per milestone hit (3, 7, 14, 30, 100 logs).
     * HomeScreen observes and shows a celebration overlay, then calls clearMilestone().
     */
    private val _milestoneEvent = MutableStateFlow<MilestoneEvent?>(null)
    val milestoneEvent: StateFlow<MilestoneEvent?> = _milestoneEvent.asStateFlow()

    fun clearMilestone() { _milestoneEvent.value = null }

    val trackerItems: StateFlow<List<TrackerItem>> = repository.getActiveItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<TrackerCategory>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val itemsByCategory: StateFlow<Map<TrackerCategory?, List<TrackerItem>>> =
        combine(trackerItems, categories) { items, cats ->
            val catMap = cats.associateBy { it.id }
            items.groupBy { item -> item.categoryId?.let { catMap[it] } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun logCompletion(item: TrackerItem, completedAt: Long? = null, notes: String? = null) {
        viewModelScope.launch {
            try {
                val log = if (completedAt != null) {
                    repository.logCompletionAt(item.id, completedAt, notes)
                } else {
                    repository.logCompletion(item.id, notes)
                }
                _undoState.value = UndoState(
                    logId = log.id,
                    trackerItemId = item.id,
                    previousCompletedAt = item.lastCompletedAt
                )

                // Optional opt-in success chime (off by default, respects silent mode).
                successSoundService.playIfEnabled()

                // Check for streak milestone (3/7/14/30/100 total logs)
                val count = repository.getLogCountForTracker(item.id)
                StreakUtil.milestoneFor(count)?.let { milestone ->
                    _milestoneEvent.value = MilestoneEvent(
                        trackerName = item.name,
                        milestone = milestone,
                        label = StreakUtil.labelFor(milestone)
                    )
                }

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

    /**
     * Pull-to-refresh: kicks off a sync and holds isRefreshing=true until
     * SupabaseService reports isSyncing=false (or a safety timeout).
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                supabaseService.syncOnForeground()
                // Wait briefly for sync to begin, then wait for it to finish.
                delay(200)
                runCatching {
                    supabaseService.isSyncing.first { syncing -> !syncing }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
