package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.ui.screens.BridgeAndWebScreen
import com.example.ui.screens.CockpitScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.IntelligenceScreen
import com.example.ui.theme.CockpitDeepSlate
import com.example.ui.theme.CockpitElevated
import com.example.ui.theme.CockpitObsidian
import com.example.ui.theme.DriveIqTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DriveIqViewModel

const val ROUTE_COCKPIT = "cockpit"
const val ROUTE_INTELLIGENCE = "intelligence"
const val ROUTE_HISTORY = "history"
const val ROUTE_BRIDGE = "bridge"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopDestinations = listOf(
    NavDestination(
        route = ROUTE_COCKPIT,
        labelRes = R.string.nav_cockpit,
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed,
        testTag = "nav_tab_cockpit"
    ),
    NavDestination(
        route = ROUTE_INTELLIGENCE,
        labelRes = R.string.nav_intelligence,
        selectedIcon = Icons.Filled.Analytics,
        unselectedIcon = Icons.Outlined.Analytics,
        testTag = "nav_tab_intelligence"
    ),
    NavDestination(
        route = ROUTE_HISTORY,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_tab_history"
    ),
    NavDestination(
        route = ROUTE_BRIDGE,
        labelRes = R.string.nav_bridge,
        selectedIcon = Icons.Filled.Cable,
        unselectedIcon = Icons.Outlined.Cable,
        testTag = "nav_tab_bridge"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            DriveIqTheme {
                DriveIqApp()
            }
        }
    }
}

@Composable
fun DriveIqApp(
    viewModel: DriveIqViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_COCKPIT

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aiCoachState by viewModel.aiCoachState.collectAsStateWithLifecycle()
    val pastSessions by viewModel.pastSessions.collectAsStateWithLifecycle()
    val inspectedSessionId by viewModel.inspectedSessionId.collectAsStateWithLifecycle()
    val inspectedEvents by viewModel.inspectedSessionEvents.collectAsStateWithLifecycle()
    val inspectedTelemetry by viewModel.inspectedSessionTelemetry.collectAsStateWithLifecycle()

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val speedLimits = listOf(60, 80, 100, 120)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitObsidian)
    ) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            containerColor = CockpitObsidian,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = CockpitDeepSlate,
                        contentColor = TextPrimary,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        TopDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonCyan,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = CockpitElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = CockpitDeepSlate,
                        contentColor = TextPrimary,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets(0, 0, 0, 0))
                    ) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        TopDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NeonCyan,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = CockpitElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .testTag(dest.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_COCKPIT,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_COCKPIT) {
                        CockpitScreen(
                            uiState = uiState,
                            onToggleEngine = viewModel::toggleEngine,
                            onSelectVehicle = viewModel::selectVehicle,
                            onCycleSpeedLimit = {
                                val nextIdx = (speedLimits.indexOf(uiState.speedLimitKmh) + 1) % speedLimits.size
                                viewModel.setSpeedLimit(speedLimits[nextIdx])
                            },
                            onToggleMuteAudio = viewModel::toggleMuteAudio,
                            onAcceleratorInput = viewModel::setAcceleratorInput,
                            onBrakeInput = viewModel::setBrakeInput,
                            onSteeringInput = viewModel::setSteeringInput,
                            onCenterSteering = viewModel::centerSteering,
                            onToggleAutoCenter = viewModel::toggleAutoCenterSteering,
                            onDismissWarning = viewModel::dismissWarning,
                            onResetSpareParts = viewModel::resetSparePartWearForSelectedVehicle
                        )
                    }
                    composable(ROUTE_INTELLIGENCE) {
                        IntelligenceScreen(
                            uiState = uiState,
                            aiCoachState = aiCoachState,
                            onAnalyzeLiveClick = viewModel::analyzeLiveWithAi,
                            onResetSpareParts = viewModel::resetSparePartWearForSelectedVehicle
                        )
                    }
                    composable(ROUTE_HISTORY) {
                        HistoryScreen(
                            sessions = pastSessions,
                            inspectedSessionId = inspectedSessionId,
                            inspectedEvents = inspectedEvents,
                            inspectedTelemetry = inspectedTelemetry,
                            aiCoachState = aiCoachState,
                            onInspectSession = viewModel::inspectSession,
                            onDeleteSession = viewModel::deleteSession,
                            onClearAll = viewModel::clearAllHistory,
                            onAnalyzeSession = viewModel::analyzeSessionWithAi
                        )
                    }
                    composable(ROUTE_BRIDGE) {
                        BridgeAndWebScreen(
                            bridgeStatus = uiState.bridgeStatus,
                            onSelectInputSource = viewModel::setInputSource,
                            onToggleLocalServer = viewModel::toggleLocalWebServer,
                            onConnectWebSocket = viewModel::connectCarlaWebSocket,
                            onDisconnectWebSocket = viewModel::disconnectCarlaWebSocket
                        )
                    }
                }
            }
        }
    }
}
