package com.pearsonmedia.lastlogged.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.pearsonmedia.lastlogged.service.BiometricService
import com.pearsonmedia.lastlogged.service.SupabaseService
import com.pearsonmedia.lastlogged.ui.navigation.AppNavHost
import com.pearsonmedia.lastlogged.ui.theme.LastLoggedTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var biometricService: BiometricService
    @Inject lateinit var supabaseService: SupabaseService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LastLoggedTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isUnlocked by biometricService.isUnlocked.collectAsState()

                    // Lock on background, trigger sync on foreground
                    LifecycleHandler(
                        onResume = {
                            supabaseService.syncOnForeground()
                            if (biometricService.isEnabled && !biometricService.isUnlocked.value) {
                                biometricService.authenticate(
                                    activity = this@MainActivity,
                                    onSuccess = { },
                                    onFailure = { }
                                )
                            }
                        },
                        onStop = {
                            biometricService.lock()
                        }
                    )

                    if (biometricService.isEnabled && !isUnlocked) {
                        LockScreen(
                            onAuthenticate = {
                                biometricService.authenticate(
                                    activity = this@MainActivity,
                                    onSuccess = { },
                                    onFailure = { }
                                )
                            }
                        )
                    } else {
                        val navController = rememberNavController()
                        AppNavHost(navController = navController)
                    }
                }
            }
        }
    }
}

@Composable
private fun LockScreen(onAuthenticate: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Last Logged",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Locked",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAuthenticate) {
                Text("Unlock")
            }
        }
    }
}

@Composable
private fun LifecycleHandler(
    onResume: () -> Unit = {},
    onStop: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onResume()
                Lifecycle.Event.ON_STOP -> onStop()
                else -> { }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
