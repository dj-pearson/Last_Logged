package com.pearsonmedia.lastlogged.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pearsonmedia.lastlogged.data.local.entity.CompletionLog
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import com.pearsonmedia.lastlogged.data.repository.TrackerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class DetailStats(
    val streak: Int = 0,
    val totalLogs: Int = 0,
    val avgIntervalDays: Int? = null
)

data class DetailUiState(
    val item: TrackerItem? = null,
    val category: TrackerCategory? = null,
    val logs: List<CompletionLog> = emptyList(),
    val stats: DetailStats = DetailStats(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TrackerDetailViewModel @Inject constructor(
    private val repository: TrackerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadTracker(trackerId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val item = repository.getItemById(trackerId)
            val category = item?.categoryId?.let { repository.getCategoryById(it) }

            _uiState.value = _uiState.value.copy(
                item = item,
                category = category,
                isLoading = false
            )

            // Collect logs reactively
            repository.getLogsForTrackerLimited(trackerId, 100).collect { logs ->
                _uiState.value = _uiState.value.copy(
                    logs = logs,
                    stats = computeStats(logs, item?.reminderIntervalDays ?: 0)
                )
            }
        }
    }

    fun logNow() {
        val id = _uiState.value.item?.id ?: return
        viewModelScope.launch {
            repository.logCompletion(id)
        }
    }

    fun archiveItem() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            repository.archiveItem(item.id)
        }
    }

    private fun computeStats(logs: List<CompletionLog>, reminderIntervalDays: Int): DetailStats {
        if (logs.isEmpty()) return DetailStats()

        // Logs come in descending order (newest first).
        val sorted = logs.sortedByDescending { it.completedAt }
        val dayMs = 86_400_000L

        // Average interval between consecutive logs (in days).
        val avgIntervalDays: Int? = if (sorted.size >= 2) {
            val gaps = sorted.zipWithNext { a, b -> (a.completedAt - b.completedAt).coerceAtLeast(0L) }
            val avgMs = gaps.average()
            (avgMs / dayMs).roundToInt().coerceAtLeast(1)
        } else null

        // Streak: count of most-recent consecutive logs where each gap to the previous
        // is within 1.5x the reminder interval (or within avg interval if no reminder).
        val tolerance: Long = when {
            reminderIntervalDays > 0 -> (reminderIntervalDays * 1.5 * dayMs).toLong()
            avgIntervalDays != null -> (avgIntervalDays * 1.5 * dayMs).toLong()
            else -> Long.MAX_VALUE
        }
        var streak = 1
        for (i in 0 until sorted.lastIndex) {
            val gap = sorted[i].completedAt - sorted[i + 1].completedAt
            if (gap <= tolerance) streak++ else break
        }

        return DetailStats(
            streak = streak,
            totalLogs = sorted.size,
            avgIntervalDays = avgIntervalDays
        )
    }
}
