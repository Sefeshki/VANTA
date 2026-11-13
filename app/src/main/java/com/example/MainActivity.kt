package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PermissionCenterScreen
import com.example.ui.screens.PrivacyProtectionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SimManagementScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SystemHealthScreen
import com.example.ui.screens.TelegramConfigScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val app = applicationContext as VantaApplication

        setContent {
            MyApplicationTheme {
                VantaAppNavHost(app = app)
            }
        }
    }
}

@Composable
fun VantaAppNavHost(
    app: VantaApplication,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "splash",
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) }
    ) {
        composable("splash") {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate("dashboard") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable("dashboard") {
            DashboardScreen(
                onNavigateToSims = { navController.navigate("sim_management") },
                onNavigateToTelegram = { navController.navigate("telegram_config") },
                onNavigateToPermissions = { navController.navigate("permission_center") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPrivacyProtection = { navController.navigate("privacy_protection") },
                onNavigateToSystemHealth = { navController.navigate("system_health") }
            )
        }

        composable(
            route = "sim_management",
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
            }
        ) {
            SimManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "telegram_config",
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
            }
        ) {
            TelegramConfigScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "permission_center",
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
            }
        ) {
            PermissionCenterScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "settings",
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
            }
        ) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTelegram = { navController.navigate("telegram_config") },
                onReplayTutorial = {
                    app.preferencesManager.setTutorialCompleted(false)
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "privacy_protection",
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
            }
        ) {
            PrivacyProtectionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "system_health",
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
            }
        ) {
            SystemHealthScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPermissions = { navController.navigate("permission_center") }
            )
        }
    }
}
