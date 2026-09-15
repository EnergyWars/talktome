package com.wafflehq.uikit.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wafflehq.uikit.R
import com.wafflehq.uikit.navigation.AppNavItem
import com.wafflehq.uikit.navigation.AppTopNavBar
import com.wafflehq.uikit.theme.AppTheme

enum class HeaderItem { Menu, Home, Settings, None }

@Composable
fun AppScaffold(
    activeItem: HeaderItem,
    onOpenMenu: () -> Unit,
    onNavigateHome: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = AppTheme.colors.background,
        topBar = {
            AppHeader(
                activeItem = activeItem,
                onOpenMenu = onOpenMenu,
                onNavigateHome = onNavigateHome,
                onOpenSettings = onOpenSettings,
            )
        },
        content = content,
    )
}

@Composable
fun AppHeader(
    activeItem: HeaderItem,
    onOpenMenu: () -> Unit,
    onNavigateHome: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTopNavBar(
        modifier = modifier,
        items = listOf(
            AppNavItem(
                label = stringResource(R.string.header_menu),
                icon = Icons.Outlined.Menu,
                selected = activeItem == HeaderItem.Menu,
                onClick = onOpenMenu,
            ),
            AppNavItem(
                label = stringResource(R.string.header_home),
                icon = Icons.Outlined.Home,
                selected = activeItem == HeaderItem.Home,
                onClick = onNavigateHome,
            ),
            AppNavItem(
                label = stringResource(R.string.label_settings),
                icon = Icons.Outlined.Settings,
                selected = activeItem == HeaderItem.Settings,
                onClick = onOpenSettings,
            ),
        ),
    )
}
