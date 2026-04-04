package com.pearsonmedia.lastlogged.util

import androidx.compose.runtime.Immutable
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem

/**
 * Stable wrappers for Compose recomposition optimization.
 * Room entities are data classes (stable), but wrapping collections
 * prevents unnecessary recomposition of parent composables.
 */
@Immutable
data class StableTrackerList(
    val items: List<TrackerItem>
)

@Immutable
data class StableCategoryList(
    val categories: List<TrackerCategory>
)

@Immutable
data class StableCategoryItemsMap(
    val map: Map<TrackerCategory?, List<TrackerItem>>
)
