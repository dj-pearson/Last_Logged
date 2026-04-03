package com.pearsonmedia.lastlogged.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

    LaunchedEffect(trackerId) {
        viewModel.loadTracker(trackerId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.item?.name ?: "Tracker Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            )
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
                    .padding(padding)
            ) {
                // Header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        uiState.item?.let { item ->
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            uiState.category?.let { category ->
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Last logged: ${TimeFormatUtil.elapsedTimeString(item.lastCompletedAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Reminder every ${item.reminderIntervalDays} days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                }

                // History header
                item {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Completion logs
                if (uiState.logs.isEmpty()) {
                    item {
                        Text(
                            text = "No completions yet. Tap the check button on the home screen to log your first completion.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(uiState.logs, key = { it.id }) { log ->
                        val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                        ListItem(
                            headlineContent = {
                                Text(dateFormat.format(Date(log.completedAt)))
                            },
                            supportingContent = log.notes?.let { { Text(it) } }
                        )
                    }
                }
            }
        }
    }
}
