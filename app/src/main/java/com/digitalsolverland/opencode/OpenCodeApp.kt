package com.digitalsolverland.opencode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.digitalsolverland.opencode.SecureSettings
import com.digitalsolverland.opencode.LocalServerService
import com.digitalsolverland.opencode.runtime.LocalPhase
import com.digitalsolverland.opencode.runtime.LocalServer
import com.digitalsolverland.opencode.ui.screens.ChatScreen
import com.digitalsolverland.opencode.ui.screens.LocalServerScreen
import com.digitalsolverland.opencode.ui.screens.LogsScreen
import com.digitalsolverland.opencode.ui.screens.SessionsScreen
import com.digitalsolverland.opencode.ui.screens.SettingsScreen
import com.digitalsolverland.opencode.ui.screens.TerminalScreen
import com.digitalsolverland.opencode.ui.theme.OpenCodeTheme
import com.digitalsolverland.opencode.viewmodel.OpenCodeViewModel

private const val ROUTE_SESSIONS = "sessions"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_LOCAL = "local"
private const val ROUTE_LOGS = "logs"
private const val ROUTE_TERMINAL = "terminal"

@Composable
fun OpenCodeApp(viewModel: OpenCodeViewModel = viewModel()) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val sessions by viewModel.sessions.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val models by viewModel.models.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedAgent by viewModel.selectedAgent.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()
    val activity by viewModel.activity.collectAsState()
    val error by viewModel.error.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val serverPassword by viewModel.serverPassword.collectAsState()

    val context = LocalContext.current
    val settings = remember { SecureSettings(context) }
    val localPhase by LocalServer.phase.collectAsState()
    val localPort by LocalServer.port.collectAsState()
    var autoConnectedPort by remember { mutableStateOf(-1) }

    // En mode autonome : relance le serveur local au démarrage de l'app s'il est installé.
    LaunchedEffect(Unit) {
        LocalServer.refresh(context)
        if (settings.localMode && LocalServer.installed.value) {
            ContextCompat.startForegroundService(
                context,
                LocalServerService.intent(context, LocalServerService.ACTION_START),
            )
        }
    }

    LaunchedEffect(localPhase, localPort) {
        if (localPhase == LocalPhase.RUNNING && settings.localMode && autoConnectedPort != localPort) {
            autoConnectedPort = localPort
            viewModel.connectTo("http://127.0.0.1:$localPort", LocalServer.password(context))
        }
    }

    LaunchedEffect(error) {
        val message = error
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    OpenCodeTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_SESSIONS,
                ) {
                    composable(ROUTE_SESSIONS) {
                        SessionsScreen(
                            sessions = sessions,
                            isConnected = isConnected,
                            isConnecting = isConnecting,
                            onCreateSession = {
                                viewModel.createSession {
                                    navController.navigate(ROUTE_CHAT) { launchSingleTop = true }
                                }
                            },
                            onSessionClick = { session ->
                                viewModel.openSession(session)
                                navController.navigate(ROUTE_CHAT) { launchSingleTop = true }
                            },
                            onDeleteSession = { viewModel.deleteSession(it) },
                            onRefresh = { viewModel.refresh() },
                            onSettingsClick = {
                                navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                            },
                            onLocalServerClick = {
                                navController.navigate(ROUTE_LOCAL) { launchSingleTop = true }
                            },
                        )
                    }

                    composable(ROUTE_CHAT) {
                        val session = currentSession
                        if (session == null) {
                            // Deleted while open, or process death: never leave a blank screen.
                            LaunchedEffect(Unit) {
                                if (navController.currentDestination?.route == ROUTE_CHAT) {
                                    navController.popBackStack()
                                }
                            }
                        } else {
                            ChatScreen(
                                session = session,
                                messages = messages,
                                models = models,
                                agents = agents,
                                selectedModel = selectedModel,
                                selectedAgent = selectedAgent,
                                isWorking = isWorking,
                                activity = activity,
                                onBack = { navController.popBackStack() },
                                onSendMessage = { viewModel.sendMessage(it) },
                                onStop = { viewModel.stop() },
                                onSelectModel = { viewModel.setModel(it) },
                                onSelectAgent = { viewModel.setAgent(it) },
                                onOpenSettings = {
                                    navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                                },
                                onCompact = { viewModel.compact() },
                                quickActions = viewModel.quickActions,
                            )
                        }
                    }

                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            serverUrl = serverUrl,
                            serverPassword = serverPassword,
                            isConnected = isConnected,
                            isConnecting = isConnecting,
                            lastError = connectionError,
                            onSave = { url, password -> viewModel.saveConnection(url, password) },
                            onConnect = {
                                viewModel.connect { ok ->
                                    if (ok) navController.popBackStack()
                                }
                            },
                            onDisconnect = {
                                viewModel.disconnect()
                                navController.popBackStack()
                            },
                            onOpenLocalServer = {
                                navController.navigate(ROUTE_LOCAL) { launchSingleTop = true }
                            },
                            onOpenLogs = {
                                navController.navigate(ROUTE_LOGS) { launchSingleTop = true }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(ROUTE_LOCAL) {
                        LocalServerScreen(
                            onBack = { navController.popBackStack() },
                            onInstall = {
                                ContextCompat.startForegroundService(
                                    context,
                                    LocalServerService.intent(context, LocalServerService.ACTION_INSTALL),
                                )
                            },
                            onStart = {
                                ContextCompat.startForegroundService(
                                    context,
                                    LocalServerService.intent(context, LocalServerService.ACTION_START),
                                )
                            },
                            onStop = {
                                context.startService(
                                    LocalServerService.intent(context, LocalServerService.ACTION_STOP),
                                )
                            },
                            onUseLocalServer = {
                                settings.localMode = true
                                viewModel.connectTo(
                                    "http://127.0.0.1:${LocalServer.port.value}",
                                    LocalServer.password(context),
                                ) { ok ->
                                    if (ok) navController.popBackStack()
                                }
                            },
                            onOpenLogs = {
                                navController.navigate(ROUTE_LOGS) { launchSingleTop = true }
                            },
                            onOpenTerminal = {
                                navController.navigate(ROUTE_TERMINAL) { launchSingleTop = true }
                            },
                        )
                    }

                    composable(ROUTE_LOGS) {
                        LogsScreen(onBack = { navController.popBackStack() })
                    }

                    composable(ROUTE_TERMINAL) {
                        TerminalScreen(onBack = { navController.popBackStack() })
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
