package com.honeyjar.app

import android.provider.Settings
import android.content.ComponentName
import android.text.TextUtils
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.honeyjar.app.ui.screens.HomeScreen
import com.honeyjar.app.ui.screens.HistoryScreen
import com.honeyjar.app.ui.screens.StatsScreen
import com.honeyjar.app.ui.screens.SettingsScreen
import com.honeyjar.app.ui.screens.OnboardingScreen
import com.honeyjar.app.ui.theme.HoneyJarTheme
import com.honeyjar.app.ui.theme.HoneyJarThemeType
import com.honeyjar.app.data.ThemePrefs
import com.honeyjar.app.ui.screens.areAllPermissionsGranted
import androidx.lifecycle.viewmodel.compose.viewModel
import com.honeyjar.app.data.database.HoneyJarDatabase
import com.honeyjar.app.repositories.PriorityRepository
import com.honeyjar.app.ui.viewmodels.MainViewModel
import com.honeyjar.app.ui.viewmodels.MainViewModelFactory
import com.honeyjar.app.repositories.NotificationRepository
import com.honeyjar.app.repositories.SettingsRepository
import com.honeyjar.app.utils.AppIconCache
import com.honeyjar.app.utils.AppLabelCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.honeyjar.app.ui.screens.OnboardingMode

class MainActivity : FragmentActivity() {
    private val database by lazy { HoneyJarDatabase.getDatabase(this) }
    private val repository by lazy { PriorityRepository(database.priorityGroupDao()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePrefs.initialize(this)
        NotificationRepository.initialize(database.notificationDao(), database.statsDao())

        // Pre-warm app label cache on IO thread so first tab visit has labels ready.
        val appContext = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val notifications = NotificationRepository.notifications.first()
            notifications.map { it.packageName }.distinct()
                .forEach {
                    AppLabelCache.get(it, appContext)
                    AppIconCache.get(it, appContext)
                }
        }

        setContent {
            val currentTheme = ThemePrefs.theme.value
            val scope = rememberCoroutineScope()
            val hasCompletedOnboardingIntro by SettingsRepository
                .hasCompletedOnboardingIntro(this@MainActivity)
                .collectAsState(null)

            // Wait for DataStore to emit before rendering anything — prevents a
            // one-frame flash to the Onboarding screen on every cold start.
            if (hasCompletedOnboardingIntro == null) return@setContent

            val startDestination = if (!hasCompletedOnboardingIntro!! || !areAllPermissionsGranted(this@MainActivity)) {
                Screen.Onboarding.route
            } else {
                Screen.Home.route
            }

            val onboardingMode = if (hasCompletedOnboardingIntro!!) {
                OnboardingMode.PermissionsOnly
            } else {
                OnboardingMode.Full
            }

            HoneyJarTheme(themeType = currentTheme) {
                MainAppScaffold(
                    currentTheme = currentTheme,
                    onThemeChange = { ThemePrefs.setTheme(this, it) },
                    viewModel = viewModel(factory = MainViewModelFactory(this@MainActivity.application, repository, database.statsDao(), database.notificationDao())),
                    startDestination = startDestination,
                    onboardingMode = onboardingMode,
                    onOnboardingIntroCompleted = {
                        scope.launch {
                            SettingsRepository.setHasCompletedOnboardingIntro(this@MainActivity, true)
                        }
                    }
                )
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector?) {
    object Onboarding : Screen("onboarding", "Onboarding", null)
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object History : Screen("history", "History", Icons.Filled.History)
    object Stats : Screen("stats", "Stats", Icons.Filled.Assessment)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    currentTheme: HoneyJarThemeType, 
    onThemeChange: (HoneyJarThemeType) -> Unit,
    viewModel: MainViewModel,
    startDestination: String,
    onboardingMode: OnboardingMode,
    onOnboardingIntroCompleted: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.Onboarding.route) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 8.dp
                ) {
                    val items = listOf(Screen.Home, Screen.History, Screen.Stats, Screen.Settings)
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon!!, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = startDestination, Modifier.padding(innerPadding)) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    mode = onboardingMode,
                    onFinished = {
                        onOnboardingIntroCompleted()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) {
                                inclusive = true
                            }
                        }
                    }
                )
            }
            composable(Screen.Home.route) { 
                HomeScreen(
                    viewModel, 
                    onNavigateToHistory = { filter, status ->
                        val route = StringBuilder(Screen.History.route)
                        val params = mutableListOf<String>()
                        if (filter != null) params.add("filter=$filter")
                        if (status != null) params.add("status=$status")
                        
                        if (params.isNotEmpty()) {
                            route.append("?").append(params.joinToString("&"))
                        }
                        
                        navController.navigate(route.toString()) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                ) 
            }
            composable(Screen.History.route + "?filter={filter}&status={status}") { backStackEntry ->
                val filter = backStackEntry.arguments?.getString("filter")
                val status = backStackEntry.arguments?.getString("status")
                HistoryScreen(viewModel, filter, status)
            }
            composable(Screen.Stats.route) { StatsScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(currentTheme, onThemeChange, viewModel) }
        }
    }
}


