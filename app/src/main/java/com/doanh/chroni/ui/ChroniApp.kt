package com.doanh.chroni.ui

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val HOME = "home"
    const val RECORD = "record"
    const val RESULT = "result/{id}"
    const val ANALYSIS = "analysis/{id}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    fun result(id: String) = "result/$id"
    fun analysis(id: String) = "analysis/$id"
}

@Composable
fun ChroniApp(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm,
                    onStart = { nav.navigate(Routes.RECORD) { launchSingleTop = true } },
                    onHistory = { nav.navigate(Routes.HISTORY) { launchSingleTop = true } },
                    onSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                )
            }
            composable(Routes.RECORD) {
                RecordingScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onResult = { id -> nav.navigate(Routes.result(id)) { popUpTo(Routes.HOME) } },
                )
            }
            composable(Routes.RESULT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                ResultScreen(
                    vm, id,
                    onBack = { nav.popBackStack() },
                    onAnalysis = { nav.navigate(Routes.analysis(id)) { launchSingleTop = true } },
                    onNewMeasurement = {
                        if (vm.validateInputs()) nav.navigate(Routes.RECORD) { popUpTo(Routes.HOME) }
                        else nav.popBackStack(Routes.HOME, false)
                    },
                )
            }
            composable(Routes.ANALYSIS, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                AnalysisScreen(vm, id, onBack = { nav.popBackStack() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(vm, onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate(Routes.result(id)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
