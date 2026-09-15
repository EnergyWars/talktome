package com.wafflehq.talktome.ui.navigation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wafflehq.talktome.R
import com.wafflehq.talktome.ui.compose.ComposeMessageScreen
import com.wafflehq.talktome.ui.gemini.GeminiSettingsScreen
import com.wafflehq.talktome.ui.home.HomeScreen
import com.wafflehq.talktome.ui.inbox.InboxScreen
import com.wafflehq.talktome.ui.notes.NotesScreen
import com.wafflehq.talktome.ui.pairing.PairingScreen
import com.wafflehq.talktome.ui.profile.ProfileScreen
import com.wafflehq.talktome.ui.settings.DisplaySettingsScreen
import com.wafflehq.talktome.ui.settings.SettingsScreen
import com.wafflehq.talktome.ui.venting.VentingScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.wafflehq.uikit.navigation.AppNavItem
import com.wafflehq.uikit.navigation.AppNavSection
import com.wafflehq.uikit.navigation.AppSideNavDrawer
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SETTINGS_DISPLAY = "settings_display"
    const val PROFILE = "profile"
    const val GEMINI_SETTINGS = "gemini_settings"
    const val PAIRING = "pairing"
    const val COMPOSE_MESSAGE = "compose_message"
    const val VENTING = "venting"
    const val INBOX = "inbox"
    const val NOTES = "notes"
}

private fun NavController.switchTo(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(Routes.HOME) { saveState = true }
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }
    val navigateHome: () -> Unit = { navController.switchTo(Routes.HOME) }
    val openSettings: () -> Unit = {
        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
    }
    val navigateFromDrawer: (String) -> Unit = { route ->
        scope.launch { drawerState.close() }
        navController.navigate(route) { launchSingleTop = true }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppSideNavDrawer(
                title = stringResource(R.string.app_name),
                sections = listOf(
                    AppNavSection(
                        label = stringResource(R.string.drawer_section_talk),
                        items = listOf(
                            AppNavItem(
                                label = stringResource(R.string.settings_row_compose),
                                icon = Icons.AutoMirrored.Outlined.Send,
                                selected = currentRoute == Routes.COMPOSE_MESSAGE,
                                onClick = { navigateFromDrawer(Routes.COMPOSE_MESSAGE) },
                            ),
                            AppNavItem(
                                label = stringResource(R.string.settings_row_venting),
                                icon = Icons.Outlined.SelfImprovement,
                                selected = currentRoute == Routes.VENTING,
                                onClick = { navigateFromDrawer(Routes.VENTING) },
                            ),
                            AppNavItem(
                                label = stringResource(R.string.settings_row_inbox),
                                icon = Icons.Outlined.MarkEmailRead,
                                selected = currentRoute == Routes.INBOX,
                                onClick = { navigateFromDrawer(Routes.INBOX) },
                            ),
                        ),
                    ),
                    AppNavSection(
                        label = stringResource(R.string.drawer_section_configuration),
                        items = listOf(
                            AppNavItem(
                                label = stringResource(R.string.settings_row_profile),
                                icon = Icons.AutoMirrored.Outlined.Chat,
                                selected = currentRoute == Routes.PROFILE,
                                onClick = { navigateFromDrawer(Routes.PROFILE) },
                            ),
                            AppNavItem(
                                label = stringResource(R.string.settings_row_pairing),
                                icon = Icons.Outlined.Link,
                                selected = currentRoute == Routes.PAIRING,
                                onClick = { navigateFromDrawer(Routes.PAIRING) },
                            ),
                            AppNavItem(
                                label = stringResource(R.string.settings_row_gemini),
                                icon = Icons.Outlined.SmartToy,
                                selected = currentRoute == Routes.GEMINI_SETTINGS,
                                onClick = { navigateFromDrawer(Routes.GEMINI_SETTINGS) },
                            ),
                            AppNavItem(
                                label = stringResource(R.string.settings_row_notes),
                                icon = Icons.AutoMirrored.Outlined.Notes,
                                selected = currentRoute == Routes.NOTES,
                                onClick = { navigateFromDrawer(Routes.NOTES) },
                            ),
                        ),
                    ),
                ),
            )
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenMenu = openMenu,
                    onNavigateHome = navigateHome,
                    onOpenSettings = openSettings,
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenGeminiSettings = { navController.navigate(Routes.GEMINI_SETTINGS) },
                    onOpenPairing = { navController.navigate(Routes.PAIRING) },
                    onOpenCompose = { navController.navigate(Routes.COMPOSE_MESSAGE) },
                    onOpenVenting = { navController.navigate(Routes.VENTING) },
                    onOpenInbox = { navController.navigate(Routes.INBOX) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDisplay = { navController.navigate(Routes.SETTINGS_DISPLAY) },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenGeminiSettings = { navController.navigate(Routes.GEMINI_SETTINGS) },
                    onOpenPairing = { navController.navigate(Routes.PAIRING) },
                    onOpenNotes = { navController.navigate(Routes.NOTES) },
                )
            }
            composable(Routes.SETTINGS_DISPLAY) {
                DisplaySettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GEMINI_SETTINGS) {
                GeminiSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PAIRING) {
                PairingScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.COMPOSE_MESSAGE) {
                ComposeMessageScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.VENTING) {
                VentingScreen(
                    onBack = { navController.popBackStack() },
                    onSwitchToCompose = {
                        navController.navigate(Routes.COMPOSE_MESSAGE) {
                            popUpTo(Routes.VENTING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.INBOX) {
                InboxScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.NOTES) {
                NotesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
