package com.pearsonmedia.lastlogged.ui.home

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import com.pearsonmedia.lastlogged.ui.theme.UrgencyColors
import com.pearsonmedia.lastlogged.util.AccessibilityUtil
import com.pearsonmedia.lastlogged.util.TimeFormatUtil
import kotlinx.coroutines.delay

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
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val milestone by viewModel.milestoneEvent.collectAsState()
    var showAddMenu by remember { mutableStateOf(false) }
    var quickLogItem by remember { mutableStateOf<TrackerItem?>(null) }
    val reduceMotion = AccessibilityUtil.rememberReduceMotion()
    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) { viewModel.refresh() }
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullState.endRefresh()
    }

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
                .nestedScroll(pullState.nestedScrollConnection)
        ) {
            if (itemsByCategory.isEmpty() || itemsByCategory.values.all { it.isEmpty() }) {
                EmptyState(
                    onAddTracker = onNavigateToAddTracker,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
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
                                reduceMotion = reduceMotion,
                                onLog = { viewModel.logCompletion(trackerItem) },
                                onClick = { onNavigateToDetail(trackerItem.id) },
                                onLongClick = { quickLogItem = trackerItem },
                                onArchive = { viewModel.archiveItem(trackerItem) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            // Milestone celebration overlay
            milestone?.let { ev ->
                MilestoneCelebration(
                    event = ev,
                    reduceMotion = reduceMotion,
                    onDismiss = { viewModel.clearMilestone() }
                )
            }

            // Branded pull-to-refresh indicator
            PullToRefreshContainer(
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            )

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

            // Quick-log bottom sheet (long-press on a tracker row)
            quickLogItem?.let { item ->
                QuickLogSheet(
                    item = item,
                    onDismiss = { quickLogItem = null },
                    onConfirm = { completedAt, notes ->
                        viewModel.logCompletion(item, completedAt = completedAt, notes = notes)
                        quickLogItem = null
                    }
                )
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
        text = (category?.name ?: "Uncategorized").uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
    )
}

private data class UrgencyPalette(
    val accent: Color,
    val soft: Color,
    val label: String
)

@Composable
private fun urgencyPaletteFor(
    lastCompletedAt: Long?,
    reminderIntervalDays: Int?
): UrgencyPalette {
    return when (TimeFormatUtil.urgencyLevel(lastCompletedAt, reminderIntervalDays)) {
        TimeFormatUtil.UrgencyLevel.GOOD -> UrgencyPalette(UrgencyColors.Good, UrgencyColors.GoodSoft, "up to date")
        TimeFormatUtil.UrgencyLevel.DUE_SOON -> UrgencyPalette(UrgencyColors.DueSoon, UrgencyColors.DueSoonSoft, "due soon")
        TimeFormatUtil.UrgencyLevel.OVERDUE -> UrgencyPalette(UrgencyColors.Overdue, UrgencyColors.OverdueSoft, "overdue")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackerRow(
    item: TrackerItem,
    reduceMotion: Boolean,
    onLog: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onArchive: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val indication = androidx.compose.foundation.LocalIndication.current
    val palette = urgencyPaletteFor(item.lastCompletedAt, item.reminderIntervalDays)
    val elapsedText = TimeFormatUtil.elapsedTimeString(item.lastCompletedAt)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (pressed && !reduceMotion) 0.97f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (reduceMotion) tween(0) else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "trackerCardScale"
    )

    // Log button success animation
    var showSuccess by remember { mutableStateOf(false) }
    val logScale by animateFloatAsState(
        targetValue = if (showSuccess && !reduceMotion) 1.25f else 1f,
        animationSpec = if (reduceMotion) tween(0) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "logButtonScale"
    )
    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(500)
            showSuccess = false
        }
    }

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
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 24.dp),
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .scale(scale)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .semantics { contentDescription = AccessibilityUtil.trackerRowDescription(item) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 1.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                palette.soft,
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading icon badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(color = palette.soft, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TaskAlt,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Name and time
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = elapsedText,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Log button with success animation
                IconButton(
                    onClick = {
                        onLog()
                        showSuccess = true
                        performLogHaptic(context, view)
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .scale(logScale)
                        .semantics { contentDescription = AccessibilityUtil.logButtonDescription(item.name) }
                ) {
                    Icon(
                        imageVector = if (showSuccess) Icons.Default.Check else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (showSuccess) UrgencyColors.Good else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Richer haptic for log action. Uses HapticFeedbackConstants.CONFIRM on API 30+,
 * falls back to a short Vibrator one-shot on older devices.
 */
private fun performLogHaptic(context: android.content.Context, view: android.view.View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        return
    }
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
}

@Composable
private fun EmptyState(
    onAddTracker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.TaskAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Start Tracking What Matters",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap + to add your first tracker and never forget the small things again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        androidx.compose.material3.FilledTonalButton(onClick = onAddTracker) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add your first tracker")
        }
    }
}

/**
 * Quick-log bottom sheet: long-press a tracker row to log with optional
 * custom completion time and notes, without leaving the home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickLogSheet(
    item: TrackerItem,
    onDismiss: () -> Unit,
    onConfirm: (completedAt: Long, notes: String?) -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val context = LocalContext.current
    val view = LocalView.current
    var notes by remember { mutableStateOf("") }
    var completedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Log this completion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Date row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("When:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.width(12.dp))
                androidx.compose.material3.AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(TimeFormatUtil.formatDate(completedAt)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.material3.OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(20.dp))

            androidx.compose.material3.Button(
                onClick = {
                    performLogHaptic(context, view)
                    onConfirm(completedAt, notes.ifBlank { null })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log completion", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showDatePicker) {
            val datePickerState = androidx.compose.material3.rememberDatePickerState(
                initialSelectedDateMillis = completedAt
            )
            androidx.compose.material3.DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { completedAt = it }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                androidx.compose.material3.DatePicker(state = datePickerState)
            }
        }
    }
}

/**
 * Tasteful milestone celebration shown when a tracker crosses a streak threshold
 * (3/7/14/30/100 logs). Auto-dismisses after 2 seconds. Honors reduced motion.
 */
@Composable
private fun MilestoneCelebration(
    event: MilestoneEvent,
    reduceMotion: Boolean,
    onDismiss: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduceMotion) tween(0) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "milestoneScale"
    )

    LaunchedEffect(event) {
        delay(2000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(32.dp)
                .scale(scale)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFFC107),
                                    Color(0xFFFF9800)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = event.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.trackerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
