package com.pearsonmedia.lastlogged.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pearsonmedia.lastlogged.ui.theme.UrgencyColors
import com.pearsonmedia.lastlogged.util.TimeFormatUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(
    trackerId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    viewModel: TrackerDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(trackerId) {
        viewModel.loadTracker(trackerId)
    }

    val urgency = uiState.item?.let {
        TimeFormatUtil.urgencyLevel(it.lastCompletedAt, it.reminderIntervalDays)
    } ?: TimeFormatUtil.UrgencyLevel.GOOD

    val (urgencyColor, urgencyTint) = when (urgency) {
        TimeFormatUtil.UrgencyLevel.GOOD -> UrgencyColors.Good to UrgencyColors.GoodSoft
        TimeFormatUtil.UrgencyLevel.DUE_SOON -> UrgencyColors.DueSoon to UrgencyColors.DueSoonSoft
        TimeFormatUtil.UrgencyLevel.OVERDUE -> UrgencyColors.Overdue to UrgencyColors.OverdueSoft
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Box {
                // Gradient background tinted by urgency, behind the large app bar.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    urgencyTint,
                                    Color.Transparent
                                )
                            )
                        )
                )
                LargeTopAppBar(
                    title = {
                        Text(
                            text = uiState.item?.name ?: "Tracker Detail",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        floatingActionButton = {
            if (uiState.item != null) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.logNow() },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Log now", fontWeight = FontWeight.SemiBold) },
                    containerColor = urgencyColor,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                uiState.item?.let { item ->
                    item {
                        HeroSummary(
                            categoryName = uiState.category?.name,
                            lastLoggedLabel = TimeFormatUtil.elapsedTimeString(item.lastCompletedAt),
                            reminderIntervalDays = item.reminderIntervalDays,
                            urgencyColor = urgencyColor
                        )
                    }
                    item {
                        StatsRow(
                            stats = uiState.stats,
                            urgencyColor = urgencyColor
                        )
                    }
                }

                item {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }

                if (uiState.logs.isEmpty()) {
                    item {
                        Text(
                            text = "No completions yet. Tap Log now to record your first one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    val lastIndex = uiState.logs.lastIndex
                    items(
                        count = uiState.logs.size,
                        key = { uiState.logs[it].id }
                    ) { index ->
                        val log = uiState.logs[index]
                        TimelineEntry(
                            isFirst = index == 0,
                            isLast = index == lastIndex,
                            timestamp = log.completedAt,
                            notes = log.notes,
                            tint = urgencyColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSummary(
    categoryName: String?,
    lastLoggedLabel: String,
    reminderIntervalDays: Int,
    urgencyColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
    ) {
        if (!categoryName.isNullOrBlank()) {
            Text(
                text = categoryName.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = urgencyColor
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = "Last logged $lastLoggedLabel",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (reminderIntervalDays > 0) {
            Text(
                text = "Reminder every $reminderIntervalDays days",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatsRow(
    stats: DetailStats,
    urgencyColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.LocalFireDepartment,
            label = "Streak",
            value = if (stats.streak > 0) stats.streak.toString() else "—",
            accent = urgencyColor
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.TaskAlt,
            label = "Total logs",
            value = stats.totalLogs.toString(),
            accent = MaterialTheme.colorScheme.primary
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Schedule,
            label = "Avg interval",
            value = stats.avgIntervalDays?.let { "${it}d" } ?: "—",
            accent = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val timelineFormatter: SimpleDateFormat by lazy {
    SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
}

@Composable
private fun TimelineEntry(
    isFirst: Boolean,
    isLast: Boolean,
    timestamp: Long,
    notes: String?,
    tint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(IntrinsicSize.Min)
    ) {
        // Timeline rail with dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .background(
                        if (isFirst) Color.Transparent
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f, fill = true)
                    .background(
                        if (isLast) Color.Transparent
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = timelineFormatter.format(Date(timestamp)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!notes.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
