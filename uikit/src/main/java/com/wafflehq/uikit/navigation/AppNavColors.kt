package com.wafflehq.uikit.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.wafflehq.uikit.theme.AppTheme

@Immutable
data class AppNavColors(
    val topBarBackground: Color,
    val topBarDivider: Color,
    val topBarSelectedPillBackground: Color,
    val topBarSelectedIcon: Color,
    val topBarSelectedLabel: Color,
    val topBarUnselectedIcon: Color,
    val topBarUnselectedLabel: Color,
    val drawerBackground: Color,
    val drawerTitle: Color,
    val drawerSectionLabel: Color,
    val selectedPill: Color,
    val drawerSelectedContainer: Color,
    val drawerUnselectedContainer: Color,
    val drawerSelectedIcon: Color,
    val drawerSelectedLabel: Color,
    val drawerUnselectedIcon: Color,
    val drawerUnselectedLabel: Color,
) {
    companion object {
        @Composable
        fun fromAppTheme(): AppNavColors {
            val colors = AppTheme.colors
            return AppNavColors(
                topBarBackground = colors.surface,
                topBarDivider = colors.outline,
                topBarSelectedPillBackground = colors.primary.container,
                topBarSelectedIcon = colors.primary.onContainer,
                topBarSelectedLabel = colors.onSurface,
                topBarUnselectedIcon = colors.onSurfaceVariant,
                topBarUnselectedLabel = colors.onSurfaceVariant,
                drawerBackground = colors.surface,
                drawerTitle = colors.onSurface,
                drawerSectionLabel = colors.onSurfaceVariant,
                selectedPill = colors.primary.accent,
                drawerSelectedContainer = colors.success.container,
                drawerUnselectedContainer = Color.Transparent,
                drawerSelectedIcon = colors.success.onContainer,
                drawerSelectedLabel = colors.success.onContainer,
                drawerUnselectedIcon = colors.onSurfaceVariant,
                drawerUnselectedLabel = colors.onSurfaceVariant,
            )
        }
    }
}

val LocalAppNavColors = staticCompositionLocalOf<AppNavColors?> { null }

@Composable
fun appNavColors(): AppNavColors = LocalAppNavColors.current ?: AppNavColors.fromAppTheme()
