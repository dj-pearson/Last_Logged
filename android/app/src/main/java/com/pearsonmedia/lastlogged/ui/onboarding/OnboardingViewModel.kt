package com.pearsonmedia.lastlogged.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import com.pearsonmedia.lastlogged.data.repository.TrackerRepository
import com.pearsonmedia.lastlogged.service.AnalyticsService
import com.pearsonmedia.lastlogged.service.SecureStorageService
import com.pearsonmedia.lastlogged.util.CategoryTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: TrackerRepository,
    private val secureStorageService: SecureStorageService,
    private val analyticsService: AnalyticsService
) : ViewModel() {

    private val _selectedCategories = MutableStateFlow<Set<String>>(emptySet())
    val selectedCategories: StateFlow<Set<String>> = _selectedCategories.asStateFlow()

    fun toggleCategory(name: String) {
        _selectedCategories.value = _selectedCategories.value.let { current ->
            if (current.contains(name)) current - name else current + name
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            // Seed default categories
            val categoryMap = mutableMapOf<String, String>()
            CategoryTemplates.defaultCategories.forEachIndexed { index, template ->
                val id = UUID.randomUUID().toString()
                categoryMap[template.name] = id
                repository.insertCategories(
                    listOf(
                        TrackerCategory(
                            id = id,
                            name = template.name,
                            iconName = template.iconName,
                            colorHex = template.colorHex,
                            sortOrder = index,
                            isDefault = true
                        )
                    )
                )
            }

            // Seed template items for selected categories
            var sortOrder = 0
            _selectedCategories.value.forEach { categoryName ->
                val categoryId = categoryMap[categoryName] ?: return@forEach
                val templates = CategoryTemplates.templatesFor(categoryName)
                templates.forEach { template ->
                    repository.createItem(
                        name = template.name,
                        categoryId = categoryId,
                        reminderIntervalDays = template.reminderIntervalDays,
                        iconName = template.iconName
                    )
                    sortOrder++
                }
            }

            secureStorageService.setOnboardingCompleted(true)
            analyticsService.trackOnboardingCompleted()
        }
    }
}
