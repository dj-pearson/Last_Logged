package com.pearsonmedia.lastlogged.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import com.pearsonmedia.lastlogged.data.repository.TrackerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddTrackerViewModel @Inject constructor(
    private val repository: TrackerRepository
) : ViewModel() {

    val categories: StateFlow<List<TrackerCategory>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun loadTracker(id: String): TrackerItem? {
        return repository.getItemById(id)
    }

    fun createTracker(name: String, categoryId: String?, reminderDays: Int) {
        viewModelScope.launch {
            repository.createItem(
                name = name,
                categoryId = categoryId,
                reminderIntervalDays = reminderDays,
                iconName = "checklist"
            )
        }
    }

    fun updateTracker(id: String, name: String, categoryId: String?, reminderDays: Int) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { existing ->
                repository.updateItem(
                    existing.copy(
                        name = name,
                        categoryId = categoryId,
                        reminderIntervalDays = reminderDays
                    )
                )
            }
        }
    }
}
