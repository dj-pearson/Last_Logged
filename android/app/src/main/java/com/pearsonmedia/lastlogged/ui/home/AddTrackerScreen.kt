package com.pearsonmedia.lastlogged.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pearsonmedia.lastlogged.R
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.util.InputSanitizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTrackerScreen(
    onNavigateBack: () -> Unit,
    editingTrackerId: String?,
    viewModel: AddTrackerViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val isEditing = editingTrackerId != null

    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<TrackerCategory?>(null) }
    var reminderDays by remember { mutableIntStateOf(30) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    // Load existing tracker data for editing
    LaunchedEffect(editingTrackerId) {
        if (editingTrackerId != null) {
            viewModel.loadTracker(editingTrackerId)?.let { item ->
                name = item.name
                reminderDays = item.reminderIntervalDays
            }
        }
    }

    val isFormValid = name.isNotBlank() && name.length <= InputSanitizer.MAX_TRACKER_NAME
    val remainingChars = InputSanitizer.remainingCharacters(name, InputSanitizer.MAX_TRACKER_NAME)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                        Text(
                            stringResource(
                                if (isEditing) R.string.edit_tracker_title
                                else R.string.add_tracker_title
                            )
                        )
                    },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_a11y)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(InputSanitizer.MAX_TRACKER_NAME) },
                label = { Text(stringResource(R.string.tracker_name_label)) },
                supportingText = {
                    if (remainingChars < 20) {
                        Text(pluralStringResource(R.plurals.characters_remaining, remainingChars, remainingChars))
                    }
                },
                isError = name.isNotBlank() && name.length > InputSanitizer.MAX_TRACKER_NAME,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category dropdown
            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "No Category",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.category_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.no_category)) },
                        onClick = {
                            selectedCategory = null
                            categoryMenuExpanded = false
                        }
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                selectedCategory = category
                                categoryMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reminder interval
            OutlinedTextField(
                value = reminderDays.toString(),
                onValueChange = { input ->
                    input.toIntOrNull()?.let { reminderDays = it.coerceIn(1, 3650) }
                },
                label = { Text(stringResource(R.string.reminder_interval_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            Button(
                onClick = {
                    if (!isSaved) {
                        isSaved = true
                        val sanitizedName = InputSanitizer.sanitizeTrackerName(name)
                        if (isEditing && editingTrackerId != null) {
                            viewModel.updateTracker(editingTrackerId, sanitizedName, selectedCategory?.id, reminderDays)
                        } else {
                            viewModel.createTracker(sanitizedName, selectedCategory?.id, reminderDays)
                        }
                        onNavigateBack()
                    }
                },
                enabled = isFormValid && !isSaved,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (isEditing) R.string.save_changes else R.string.create_tracker))
            }
        }
    }
}
