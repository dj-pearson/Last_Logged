package com.pearsonmedia.lastlogged.ui.home

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import com.pearsonmedia.lastlogged.util.TimeFormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAddTracker: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToEditTracker: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val itemsByCategory by viewModel.itemsByCategory.collectAsState()
    val undoState by viewModel.undoState.collectAsState()
    val error by viewModel.error.collectAsState()
    var showAddMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Last Logged", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.semantics { contentDescription = "Settings" }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showAddMenu = true },
                            modifier = Modifier.semantics { contentDescription = "Add tracker" }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Tracker") },
                                onClick = {
                                    showAddMenu = false
                                    onNavigateToAddTracker()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (itemsByCategory.isEmpty() || itemsByCategory.values.all { it.isEmpty() }) {
                EmptyState(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsByCategory.forEach { (category, items) ->
                        item(key = "header_${category?.id ?: "uncategorized"}") {
                            CategoryHeader(category)
                        }
                        items(
                            items = items,
                            key = { it.id }
                        ) { trackerItem ->
                            TrackerRow(
                                item = trackerItem,
                                onLog = { viewModel.logCompletion(trackerItem) },
                                onClick = { onNavigateToDetail(trackerItem.id) },
                                onEdit = { onNavigateToEditTracker(trackerItem.id) },
                                onArchive = { viewModel.archiveItem(trackerItem) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            // Undo Snackbar
            AnimatedVisibility(
                visible = undoState != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.undoLastLog() }) {
                            Text("Undo", color = MaterialTheme.colorScheme.inversePrimary)
                        }
                    }
                ) {
                    Text("Logged!")
                }
            }

            // Error Snackbar
            error?.let { errorMessage ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(errorMessage)
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: TrackerCategory?) {
    Text(
        text = category?.name ?: "Uncategorized",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun TrackerRow(
    item: TrackerItem,
    onLog: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    val context = LocalContext.current
    val urgency = TimeFormatUtil.urgencyLevel(item.lastCompletedAt, item.reminderIntervalDays)
    val urgencyColor = when (urgency) {
        TimeFormatUtil.UrgencyLevel.GOOD -> Color(0xFF10B981)
        TimeFormatUtil.UrgencyLevel.DUE_SOON -> Color(0xFFF59E0B)
        TimeFormatUtil.UrgencyLevel.OVERDUE -> Color(0xFFEF4444)
    }
    val elapsedText = TimeFormatUtil.elapsedTimeString(item.lastCompletedAt)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onArchive()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = "Archive",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics {
                    contentDescription = "${item.name}, $elapsedText"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Urgency indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(urgencyColor, shape = MaterialTheme.shapes.small)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Name and time
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = elapsedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = urgencyColor
                )
            }

            // Log button
            IconButton(
                onClick = {
                    onLog()
                    // Haptic feedback
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager = context.getSystemService(VibratorManager::class.java)
                        vibratorManager?.defaultVibrator?.vibrate(
                            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        val vibrator = context.getSystemService(Vibrator::class.java)
                        vibrator?.vibrate(
                            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Log ${item.name}" }
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No Trackers Yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap + to add your first tracker and start logging.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
