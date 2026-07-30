package com.pearsonmedia.lastlogged.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Launch the share sheet once the export file is on disk. Done here rather
    // than in the ViewModel because starting an Activity needs a UI context.
    LaunchedEffect(uiState.exportedFileUri) {
        val uri = uiState.exportedFileUri ?: return@LaunchedEffect
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Last Logged data export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Export data"))
        viewModel.clearExportedFile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Account Section ---
            SectionHeader("Account")
            if (uiState.isSignedIn) {
                ListItem(
                    headlineContent = { Text(uiState.userEmail ?: "Signed In") },
                    supportingContent = { Text("Cloud sync enabled") }
                )
                ListItem(
                    headlineContent = {
                        Text("Sign Out", color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable { viewModel.signOut() }
                )
            } else {
                ListItem(
                    headlineContent = { Text("Sign In") },
                    supportingContent = { Text("Enable cloud sync across devices") },
                    modifier = Modifier.clickable { onNavigateToAuth() }
                )
            }

            HorizontalDivider()

            // --- Subscription Section ---
            SectionHeader("Subscription")
            ListItem(
                headlineContent = { Text("Current Plan") },
                trailingContent = { Text(uiState.subscriptionTier) }
            )
            if (!uiState.isPremium) {
                ListItem(
                    headlineContent = {
                        Text("Upgrade to Premium", color = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable { /* TODO: Show paywall */ }
                )
            }

            HorizontalDivider()

            // --- Security Section ---
            SectionHeader("Security")
            ListItem(
                headlineContent = { Text("Require Biometric Authentication") },
                supportingContent = {
                    if (!uiState.biometricAvailable) {
                        Text("Not available on this device", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                trailingContent = {
                    Switch(
                        checked = uiState.biometricLockEnabled,
                        onCheckedChange = { viewModel.toggleBiometricLock(it) },
                        enabled = uiState.biometricAvailable
                    )
                }
            )

            HorizontalDivider()

            // --- Notifications Section ---
            SectionHeader("Notifications")
            ListItem(
                headlineContent = { Text("Enable Reminders") },
                trailingContent = {
                    Switch(
                        checked = uiState.remindersEnabled,
                        onCheckedChange = { viewModel.toggleReminders(it) }
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Default Reminder Time") },
                trailingContent = {
                    Text(
                        String.format(
                            "%02d:%02d",
                            uiState.defaultReminderHour,
                            uiState.defaultReminderMinute
                        )
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Success sound") },
                supportingContent = {
                    Text("Play a subtle chime when you log a completion. Respects silent mode.")
                },
                trailingContent = {
                    Switch(
                        checked = uiState.successSoundEnabled,
                        onCheckedChange = { viewModel.toggleSuccessSound(it) }
                    )
                }
            )

            HorizontalDivider()

            // --- Data Section ---
            SectionHeader("Data")
            ListItem(
                headlineContent = { Text("Export Data") },
                trailingContent = {
                    if (uiState.isExporting) CircularProgressIndicator()
                },
                modifier = Modifier.clickable(enabled = !uiState.isExporting) {
                    viewModel.exportData()
                }
            )
            ListItem(
                headlineContent = {
                    Text("Clear All Data", color = MaterialTheme.colorScheme.error)
                },
                trailingContent = {
                    if (uiState.isClearing) CircularProgressIndicator()
                },
                modifier = Modifier.clickable(enabled = !uiState.isClearing) {
                    viewModel.clearAllData()
                }
            )

            HorizontalDivider()

            // --- Legal Section ---
            SectionHeader("Legal")
            ListItem(
                headlineContent = { Text("Terms of Service") },
                modifier = Modifier.clickable { /* TODO: Open in browser */ }
            )
            ListItem(
                headlineContent = { Text("Privacy Policy") },
                modifier = Modifier.clickable { /* TODO: Open in browser */ }
            )

            HorizontalDivider()

            // --- About Section ---
            SectionHeader("About")
            ListItem(
                headlineContent = { Text("Version") },
                trailingContent = {
                    Text("${uiState.appVersion} (${uiState.appBuildNumber})")
                }
            )

            // --- Danger Zone ---
            if (uiState.isSignedIn) {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.showDeleteConfirmation() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text("Delete Account")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Delete Account Confirmation Dialog
        if (uiState.showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { viewModel.hideDeleteConfirmation() },
                title = { Text("Delete Account") },
                text = {
                    Column {
                        Text("This will permanently delete your account and all data. This cannot be undone.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Type DELETE to confirm:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.deleteConfirmText,
                            onValueChange = { viewModel.updateDeleteConfirmText(it) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.deleteAccount() },
                        enabled = uiState.deleteConfirmText == "DELETE" && !uiState.isDeletingAccount
                    ) {
                        if (uiState.isDeletingAccount) {
                            CircularProgressIndicator()
                        } else {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideDeleteConfirmation() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}
