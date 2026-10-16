package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.tabs.DailyScheduleTab
import com.example.ui.tabs.HistoryTab
import com.example.ui.tabs.InjectNowTab
import com.example.ui.tabs.PastDaysFillTab
import com.example.ui.tabs.SettingsTab
import kotlinx.coroutines.flow.collectLatest

enum class AppTab(val label: String, val icon: ImageVector) {
    INJECT_NOW("Inject Now", Icons.Default.Bolt),
    SCHEDULE("Schedule", Icons.Default.Schedule),
    PAST_DAYS("Past Days", Icons.Default.HistoryEdu),
    HISTORY("History", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: InjectorViewModel,
    onRequestPermissions: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsState()
    val historyLogs by viewModel.historyLogs.collectAsState()
    val latestLog by viewModel.latestSuccess.collectAsState()
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val permissionDetails by viewModel.permissionDetails.collectAsState()
    val isInjecting by viewModel.isInjecting.collectAsState()
    val backfillProgress by viewModel.backfillProgress.collectAsState()
    val healthRecords by viewModel.healthRecords.collectAsState()
    val isLoadingHealthRecords by viewModel.isLoadingHealthRecords.collectAsState()
    val selectedViewerRange by viewModel.selectedViewerRange.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (settings.isRunning) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Step Injector",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasPermissions) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (hasPermissions) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasPermissions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasPermissions) "Connected" else "No Access",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasPermissions) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                AppTab.values().forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            if (tab == AppTab.HISTORY && historyLogs.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(if (historyLogs.size > 99) "99+" else "${historyLogs.size}")
                                        }
                                    }
                                ) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> InjectNowTab(
                viewModel = viewModel,
                latestLog = latestLog,
                hasPermissions = hasPermissions,
                isInjecting = isInjecting,
                onRequestPermissions = onRequestPermissions,
                modifier = Modifier.padding(innerPadding)
            )
            1 -> DailyScheduleTab(
                viewModel = viewModel,
                settings = settings,
                latestLog = latestLog,
                modifier = Modifier.padding(innerPadding)
            )
            2 -> PastDaysFillTab(
                viewModel = viewModel,
                progress = backfillProgress,
                hasPermissions = hasPermissions,
                onRequestPermissions = onRequestPermissions,
                modifier = Modifier.padding(innerPadding)
            )
            3 -> HistoryTab(
                viewModel = viewModel,
                logs = historyLogs,
                modifier = Modifier.padding(innerPadding)
            )
            4 -> SettingsTab(
                viewModel = viewModel,
                hasPermissions = hasPermissions,
                permissionDetails = permissionDetails,
                healthRecords = healthRecords,
                selectedRange = selectedViewerRange,
                isLoadingRecords = isLoadingHealthRecords,
                onRequestPermissions = onRequestPermissions,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
