package com.rfm.onevsone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rfm.onevsone.ui.screens.*
import com.rfm.onevsone.ui.theme.OneVsOneTheme

@Composable
fun AppRoot() {
    val c = container()
    val prefs by c.settings.flow.collectAsStateWithLifecycle()
    OneVsOneTheme(prefs.theme) {
        val nav = rememberNavController()
        NavHost(nav, startDestination = "splash") {
            composable("splash") {
                SplashScreen { nav.navigate("main") { popUpTo("splash") { inclusive = true } } }
            }
            composable("main") { MainScreen(nav) }
            composable("local_setup") { LocalSetupScreen(nav) }
            composable("ai_setup") { AiSetupScreen(nav) }
            composable("create") { CreateRoomScreen(nav) }
            composable("join") { JoinRoomScreen(nav) }
            composable("room") { RoomScreen(nav) }
            composable("game") { GameScreen(nav) }
            composable("history") { HistoryScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("share") { ShareScreen(nav) }
            composable("rate") { RateScreen(nav) }
            composable("about") { AboutScreen(nav) }
        }
    }
}
