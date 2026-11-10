package com.jarvis.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jarvis.core.StartDestination
import com.jarvis.ui.screens.ApiKeyScreen
import com.jarvis.ui.screens.HomeScreen
import com.jarvis.ui.screens.IntegrityScreen
import com.jarvis.ui.screens.PermissionsScreen
import com.jarvis.ui.screens.SignInScreen
import com.jarvis.ui.screens.SplashScreen
import com.jarvis.ui.screens.settings.SettingsHubScreen
import com.jarvis.ui.screens.settings.SettingsSectionScreen
import com.jarvis.ui.theme.JarvisBackground
import com.jarvis.ui.theme.TextMuted

object Routes {
    const val SPLASH = "splash"
    const val SIGN_IN = "signin"
    const val INTEGRITY = "integrity"
    const val PERMISSIONS = "permissions"
    const val API_KEY = "apikey"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SECTION = "section/{id}"

    fun section(id: String) = "section/$id"
}

@Composable
fun JarvisNavHost(navController: NavHostController = rememberNavController()) {

    // Clears the whole wizard stack so Back never returns to a finished step.
    val resetTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen { destination ->
                val route = when (destination) {
                    StartDestination.SIGN_IN -> Routes.SIGN_IN
                    StartDestination.PERMISSIONS -> Routes.PERMISSIONS
                    StartDestination.API_KEY -> Routes.API_KEY
                    StartDestination.HOME -> Routes.HOME
                }
                resetTo(route)
            }
        }

        composable(Routes.SIGN_IN) {
            SignInScreen(onSignedIn = { resetTo(Routes.INTEGRITY) })
        }

        composable(Routes.INTEGRITY) {
            IntegrityScreen(onContinue = { resetTo(Routes.PERMISSIONS) })
        }

        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onContinue = { resetTo(Routes.API_KEY) })
        }

        composable(Routes.API_KEY) {
            ApiKeyScreen(onDone = { resetTo(Routes.HOME) })
        }

        composable(Routes.HOME) {
            HomeScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }

        composable(Routes.SETTINGS) {
            SettingsHubScreen(
                onBack = { navController.popBackStack() },
                onOpenSection = { section -> navController.navigate(Routes.section(section.id)) },
            )
        }

        composable(Routes.SECTION) { entry ->
            val id = entry.arguments?.getString("id")
            val section = SettingsSection.fromId(id)
            if (section == null) {
                JarvisBackground {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Unknown settings section.", color = TextMuted)
                    }
                }
            } else {
                SettingsSectionScreen(
                    section = section,
                    onBack = { navController.popBackStack() },
                    onRestartSetup = { resetTo(Routes.SPLASH) },
                )
            }
        }
    }
}
