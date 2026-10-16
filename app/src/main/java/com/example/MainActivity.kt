package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.example.ui.InjectorViewModel
import com.example.ui.MainScreen
import com.example.ui.theme.StepInjectorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: InjectorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StepInjectorTheme {
                // Health Connect permissions launcher
                val requestHealthPermissions = rememberLauncherForActivityResult(
                    contract = PermissionController.createRequestPermissionResultContract()
                ) {
                    viewModel.onPermissionsResult()
                }

                // Notification permissions launcher for Android 13+
                val requestNotificationPermission = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* Handled silently */ }

                // Automatically ask for permissions at first launch as requested
                LaunchedEffect(Unit) {
                    // Check notifications
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasNotification = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasNotification) {
                            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    // Check Health Connect permissions
                    viewModel.checkPermissions()
                    if (!viewModel.hasPermissions.value) {
                        val permissions = viewModel.getRequiredPermissions()
                        requestHealthPermissions.launch(permissions)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        viewModel = viewModel,
                        onRequestPermissions = {
                            requestHealthPermissions.launch(viewModel.getRequiredPermissions())
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
    }
}
