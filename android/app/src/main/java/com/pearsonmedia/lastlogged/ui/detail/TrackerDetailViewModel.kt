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

data class DetailUiState(
    val item: TrackerItem? = null,
    val category: TrackerCategory? = null,
    val logs: List<CompletionLog> = emptyList(),
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
                _uiState.value = _uiState.value.copy(logs = logs)
            }
        }
    }

    fun archiveItem() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            repository.archiveItem(item.id)
        }
    }
}
