package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PermissionCenterScreen
import com.example.ui.screens.PrivacyProtectionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SimControlScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SystemHealthScreen
import com.example.ui.screens.TelegramConfigScreen
import com.example.ui.theme.VantaTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingNavDestination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        applyImmersiveFullscreen()
        val app = applicationContext as VantaApplication
        extractDestination(intent)?.let { pendingNavDestination.value = it }

        setContent {
            val targetDest by pendingNavDestination.collectAsState()
            VantaTheme {
                VantaAppNavHost(
                    app = app,
                    pendingDestination = targetDest,
                    onConsumePendingDestination = { pendingNavDestination.value = null },
                    onReapplyFullscreen = ::applyImmersiveFullscreen
                )
            }
        }
        window.decorView.post { applyImmersiveFullscreen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDestination(intent)?.let { pendingNavDestination.value = it }
    }

    private fun extractDestination(intent: Intent?): String? {
        val raw = intent?.getStringExtra(EXTRA_NAV_DESTINATION) ?: return null
        return when (raw) {
            DEST_DASHBOARD,
            DEST_SIM_CONTROL,
            DEST_TELEGRAM_CONFIG,
            DEST_PERMISSION_CENTER,
            DEST_SYSTEM_HEALTH,
            DEST_PRIVACY_PROTECTION -> raw
            else -> null
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveFullscreen()
        (applicationContext as? VantaApplication)?.serviceAlertManager?.evaluatePermissions()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveFullscreen()
        }
    }

    private fun applyImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            if (params.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = params
            }
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
    }

    companion object {
        const val EXTRA_NAV_DESTINATION = "vanta_nav_destination"
        const val DEST_DASHBOARD = "dashboard"
        const val DEST_SIM_CONTROL = "sim_control"
        const val DEST_TELEGRAM_CONFIG = "telegram_config"
        const val DEST_PERMISSION_CENTER = "permission_center"
        const val DEST_SYSTEM_HEALTH = "system_health"
        const val DEST_PRIVACY_PROTECTION = "privacy_protection"
    }
}

@Composable
fun VantaAppNavHost(
    app: VantaApplication,
    modifier: Modifier = Modifier,
    pendingDestination: String? = null,
    onConsumePendingDestination: () -> Unit = {},
    onReapplyFullscreen: () -> Unit = {}
) {
    val navController = rememberNavController()

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ ->
            onReapplyFullscreen()
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    LaunchedEffect(pendingDestination) {
        val target = pendingDestination ?: return@LaunchedEffect
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (currentRoute != "splash") {
            if (target == MainActivity.DEST_DASHBOARD) {
                navController.navigate(MainActivity.DEST_DASHBOARD) {
                    popUpTo(MainActivity.DEST_DASHBOARD) { inclusive = true }
                }
            } else if (currentRoute != target) {
                navController.navigate(target)
            }
            onConsumePendingDestination()
        }
    }

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
                    val deepLink = pendingDestination
                    navController.navigate("dashboard") {
                        popUpTo("splash") { inclusive = true }
                    }
                    if (deepLink != null && deepLink != MainActivity.DEST_DASHBOARD) {
                        navController.navigate(deepLink)
                        onConsumePendingDestination()
                    } else if (deepLink == MainActivity.DEST_DASHBOARD) {
                        onConsumePendingDestination()
                    }
                }
            )
        }

        composable("dashboard") {
            DashboardScreen(
                onNavigateToSimControl = { navController.navigate("sim_control") },
                onNavigateToTelegram = { navController.navigate("telegram_config") },
                onNavigateToPermissions = { navController.navigate("permission_center") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPrivacyProtection = { navController.navigate("privacy_protection") },
                onNavigateToSystemHealth = { navController.navigate("system_health") }
            )
        }

        composable(
            route = "sim_control",
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
            }
        ) {
            SimControlScreen(
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
