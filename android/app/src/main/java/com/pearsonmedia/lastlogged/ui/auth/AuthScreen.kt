package com.pearsonmedia.lastlogged.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pearsonmedia.lastlogged.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (uiState.mode) {
                                AuthMode.SIGN_IN -> R.string.sign_in
                                AuthMode.SIGN_UP -> R.string.sign_up
                                AuthMode.FORGOT_PASSWORD -> R.string.reset_password_title
                                AuthMode.EMAIL_CONFIRMATION -> R.string.email_confirmation_title
                            }
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState.mode) {
                AuthMode.EMAIL_CONFIRMATION -> EmailConfirmationContent(
                    email = uiState.email,
                    onBackToSignIn = { viewModel.setMode(AuthMode.SIGN_IN) }
                )
                else -> AuthFormContent(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun AuthFormContent(
    uiState: AuthUiState,
    viewModel: AuthViewModel
) {
    var passwordVisible by remember { mutableStateOf(false) }

    // Email field
    OutlinedTextField(
        value = uiState.email,
        onValueChange = { viewModel.updateEmail(it) },
        label = { Text(stringResource(R.string.email_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        isError = uiState.email.isNotEmpty() && !viewModel.isEmailValid,
        supportingText = {
            if (uiState.email.isNotEmpty() && !viewModel.isEmailValid) {
                Text(stringResource(R.string.email_invalid), color = MaterialTheme.colorScheme.error)
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    // Password field (not shown for forgot password)
    if (uiState.mode != AuthMode.FORGOT_PASSWORD) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = { viewModel.updatePassword(it) },
            label = { Text(stringResource(R.string.password_label)) },
            visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Password validation hints (sign up only)
        if (uiState.mode == AuthMode.SIGN_UP && uiState.password.isNotEmpty()) {
            val messages = viewModel.passwordValidationMessages
            if (messages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                messages.forEach { msg ->
                    Text(
                        text = stringResource(R.string.password_rule_bullet, msg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // stringResource() is @Composable and cannot be called inside the
    // semantics {} lambda, so it is read here and captured.
    val termsA11y = stringResource(R.string.terms_agreement_a11y)

    // Terms agreement (sign up only)
    if (uiState.mode == AuthMode.SIGN_UP) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = termsA11y }
        ) {
            Checkbox(
                checked = uiState.hasAgreedToTerms,
                onCheckedChange = { viewModel.toggleTermsAgreement() }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.terms_agreement),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    // Error message
    uiState.error?.let { error ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Success message
    uiState.successMessage?.let { message ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Cooldown message
    if (uiState.cooldownSeconds > 0) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = pluralStringResource(
                R.plurals.rate_limit_cooldown,
                uiState.cooldownSeconds,
                uiState.cooldownSeconds
            ),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Submit button
    Button(
        onClick = {
            when (uiState.mode) {
                AuthMode.SIGN_IN -> viewModel.signIn()
                AuthMode.SIGN_UP -> viewModel.signUp()
                AuthMode.FORGOT_PASSWORD -> viewModel.resetPassword()
                AuthMode.EMAIL_CONFIRMATION -> { /* no-op */ }
            }
        },
        enabled = viewModel.canSubmit,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp).width(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(
                when (uiState.mode) {
                    AuthMode.SIGN_IN -> stringResource(R.string.sign_in)
                    AuthMode.SIGN_UP -> stringResource(R.string.sign_up)
                    AuthMode.FORGOT_PASSWORD -> stringResource(R.string.send_reset_link)
                    AuthMode.EMAIL_CONFIRMATION -> ""
                }
            )
        }
    }

    // Google Sign-In (only on SIGN_IN / SIGN_UP and only when configured)
    if (viewModel.isGoogleSignInAvailable &&
        (uiState.mode == AuthMode.SIGN_IN || uiState.mode == AuthMode.SIGN_UP)
    ) {
        val context = LocalContext.current
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Divider(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.auth_divider_or),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Divider(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val activity = context.findActivityForAuth()
                if (activity != null) viewModel.continueWithGoogle(activity)
            },
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(id = com.pearsonmedia.lastlogged.R.string.google_sign_in))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Mode switching buttons
    when (uiState.mode) {
        AuthMode.SIGN_IN -> {
            TextButton(onClick = { viewModel.setMode(AuthMode.SIGN_UP) }) {
                Text(stringResource(R.string.auth_no_account_prompt))
            }
            TextButton(onClick = { viewModel.setMode(AuthMode.FORGOT_PASSWORD) }) {
                Text(stringResource(R.string.forgot_password))
            }
        }
        AuthMode.SIGN_UP -> {
            TextButton(onClick = { viewModel.setMode(AuthMode.SIGN_IN) }) {
                Text(stringResource(R.string.auth_have_account_prompt))
            }
        }
        AuthMode.FORGOT_PASSWORD -> {
            TextButton(onClick = { viewModel.setMode(AuthMode.SIGN_IN) }) {
                Text(stringResource(R.string.back_to_sign_in))
            }
        }
        AuthMode.EMAIL_CONFIRMATION -> { /* handled separately */ }
    }
}

@Composable
private fun EmailConfirmationContent(
    email: String,
    onBackToSignIn: () -> Unit
) {
    Spacer(modifier = Modifier.height(32.dp))

    Icon(
        Icons.Default.Email,
        contentDescription = null,
        modifier = Modifier.height(64.dp).width(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.email_confirmation_sent_to),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )

    Text(
        text = email,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.email_confirmation_check_inbox),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 32.dp)
    )

    Spacer(modifier = Modifier.height(32.dp))

    TextButton(onClick = onBackToSignIn) {
        Text(stringResource(R.string.back_to_sign_in))
    }
}

private fun android.content.Context.findActivityForAuth(): android.app.Activity? {
    var ctx: android.content.Context = this
    while (ctx is ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
