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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pearsonmedia.lastlogged.R
import com.pearsonmedia.lastlogged.util.UrlOpener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToPaywall: () -> Unit = {},
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
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, context.getString(R.string.export_share_title)))
        viewModel.clearExportedFile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .verticalScroll(rememberScrollState())
        ) {
            // --- Account Section ---
            SectionHeader(stringResource(R.string.account_section))
            if (uiState.isSignedIn) {
                ListItem(
                    headlineContent = { Text(uiState.userEmail ?: stringResource(R.string.signed_in)) },
                    supportingContent = { Text(stringResource(R.string.cloud_sync_enabled)) }
                )
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable { viewModel.signOut() }
                )
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sign_in)) },
                    supportingContent = { Text(stringResource(R.string.sign_in_prompt)) },
                    modifier = Modifier.clickable { onNavigateToAuth() }
                )
            }

            HorizontalDivider()

            // --- Subscription Section ---
            SectionHeader(stringResource(R.string.subscription_section))
            ListItem(
                headlineContent = { Text(stringResource(R.string.current_plan)) },
                trailingContent = { Text(uiState.subscriptionTier) }
            )
            if (!uiState.isPremium) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.upgrade_to_premium), color = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable { onNavigateToPaywall() }
                )
            }

            HorizontalDivider()

            // --- Security Section ---
            SectionHeader(stringResource(R.string.security_section))
            ListItem(
                headlineContent = { Text(stringResource(R.string.biometric_lock)) },
                supportingContent = {
                    if (!uiState.biometricAvailable) {
                        Text(stringResource(R.string.biometric_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            SectionHeader(stringResource(R.string.notifications_section))
            ListItem(
                headlineContent = { Text(stringResource(R.string.enable_notifications)) },
                trailingContent = {
                    Switch(
                        checked = uiState.remindersEnabled,
                        onCheckedChange = { viewModel.toggleReminders(it) }
                    )
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.reminder_time)) },
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
                headlineContent = { Text(stringResource(R.string.success_sound)) },
                supportingContent = {
                    Text(stringResource(R.string.success_sound_description))
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
            SectionHeader(stringResource(R.string.data_section))
            ListItem(
                headlineContent = { Text(stringResource(R.string.export_data)) },
                trailingContent = {
                    if (uiState.isExporting) CircularProgressIndicator()
                },
                modifier = Modifier.clickable(enabled = !uiState.isExporting) {
                    viewModel.exportData()
                }
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.clear_all_data), color = MaterialTheme.colorScheme.error)
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
            SectionHeader(stringResource(R.string.legal_section))
            ListItem(
                headlineContent = { Text(stringResource(R.string.terms_of_service)) },
                modifier = Modifier.clickable { UrlOpener.openTerms(context) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.privacy_policy)) },
                modifier = Modifier.clickable { UrlOpener.openPrivacy(context) }
            )

            HorizontalDivider()

            // --- About Section ---
            SectionHeader(stringResource(R.string.about_section))
            ListItem(
                headlineContent = { Text(stringResource(R.string.version)) },
                trailingContent = {
                    Text(stringResource(R.string.version_value, uiState.appVersion, uiState.appBuildNumber))
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
                    Text(stringResource(R.string.delete_account))
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Delete Account Confirmation Dialog
        if (uiState.showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { viewModel.hideDeleteConfirmation() },
                title = { Text(stringResource(R.string.delete_account)) },
                text = {
                    Column {
                        Text(stringResource(R.string.delete_account_warning))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.delete_account_confirm))
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
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideDeleteConfirmation() }) {
                        Text(stringResource(R.string.cancel))
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
