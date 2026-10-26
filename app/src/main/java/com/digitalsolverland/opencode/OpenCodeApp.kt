package com.digitalsolverland.opencode

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.digitalsolverland.opencode.ui.screens.ChatScreen
import com.digitalsolverland.opencode.ui.screens.SessionsScreen
import com.digitalsolverland.opencode.ui.screens.SettingsScreen
import com.digitalsolverland.opencode.ui.theme.OpenCodeTheme
import com.digitalsolverland.opencode.viewmodel.OpenCodeViewModel

@Composable
fun OpenCodeApp() {
    val viewModel: OpenCodeViewModel = viewModel()
    val navController = rememberNavController()
    
    OpenCodeTheme {
        NavHost(
            navController = navController,
            startDestination = "sessions"
        ) {
            composable("sessions") {
                SessionsScreen(
                    sessions = viewModel.sessions.value,
                    onSessionClick = { session ->
                        viewModel.selectSession(session)
                        navController.navigate("chat")
                    },
                    onCreateSession = {
                        viewModel.createSession()
                        navController.navigate("chat")
                    },
                    onDeleteSession = { session ->
                        viewModel.deleteSession(session)
                    },
                    onSettingsClick = {
                        navController.navigate("settings")
                    }
                )
            }
            
            composable("chat") {
                val session = viewModel.currentSession.value
                if (session != null) {
                    ChatScreen(
                        session = session,
                        messages = viewModel.messages.value,
                        isLoading = viewModel.isLoading.value,
                        onBack = {
                            navController.popBackStack()
                        },
                        onSendMessage = { text ->
                            viewModel.sendMessage(text)
                        },
                        onAbort = {
                            viewModel.abortCurrentSession()
                        }
                    )
                } else {
                    navController.popBackStack()
                }
            }
            
            composable("settings") {
                SettingsScreen(
                    serverUrl = viewModel.serverUrl.value,
                    onServerUrlChange = { url ->
                        viewModel.updateServerUrl(url)
                    },
                    onConnect = {
                        viewModel.connect()
                    },
                    isConnected = viewModel.isConnected.value,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
