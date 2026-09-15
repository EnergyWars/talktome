package com.wafflehq.uikit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppRole {
    Primary,
    Secondary,
    Tertiary,
    Success,
    Warning,
    Error,
    Neutral,
}

@Immutable
data class RoleColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val tonalBorder: Color,
)

@Immutable
data class AppColors(
    val primary: RoleColors,
    val secondary: RoleColors,
    val tertiary: RoleColors,
    val success: RoleColors,
    val warning: RoleColors,
    val error: RoleColors,
    val neutral: RoleColors,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surface3: Color,
    val outline: Color,
) {
    fun forRole(role: AppRole): RoleColors = when (role) {
        AppRole.Primary -> primary
        AppRole.Secondary -> secondary
        AppRole.Tertiary -> tertiary
        AppRole.Success -> success
        AppRole.Warning -> warning
        AppRole.Error -> error
        AppRole.Neutral -> neutral
    }
}

@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val neutral: Color,
    val onNeutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
)

internal fun ColorRamp.roleColors(dark: Boolean): RoleColors = if (dark) {
    RoleColors(this["80"], this["20"], this["30"], this["90"], this["70"])
} else {
    RoleColors(this["40"], Color.White, this["90"], this["10"], this["30"])
}

internal fun buildAppColors(palette: WafflePalette, dark: Boolean): AppColors = AppColors(
    primary = palette.primary.roleColors(dark),
    secondary = palette.secondary.roleColors(dark),
    tertiary = palette.tertiary.roleColors(dark),
    success = palette.success.roleColors(dark),
    warning = palette.warning.roleColors(dark),
    error = palette.error.roleColors(dark),
    neutral = palette.neutral.roleColors(dark),
    background = if (dark) palette.darkBackground else palette.lightBackground,
    onBackground = if (dark) palette.onSurfaceDark else palette.onSurfaceLight,
    surface = if (dark) palette.darkSurface else palette.lightSurface,
    onSurface = if (dark) palette.onSurfaceDark else palette.onSurfaceLight,
    surfaceVariant = if (dark) palette.darkSurfaceVariant else palette.lightSurfaceVariant,
    onSurfaceVariant = if (dark) palette.onSurfaceVariantDark else palette.onSurfaceVariantLight,
    surface3 = if (dark) palette.darkSurface3 else palette.lightSurface3,
    outline = if (dark) palette.outlineDark else palette.outlineLight,
)

internal fun buildExtendedColors(palette: WafflePalette, dark: Boolean): ExtendedColors {
    val success = palette.success.roleColors(dark)
    val warning = palette.warning.roleColors(dark)
    val neutral = palette.neutral.roleColors(dark)
    return ExtendedColors(
        success = success.accent,
        onSuccess = success.onAccent,
        successContainer = success.container,
        onSuccessContainer = success.onContainer,
        warning = warning.accent,
        onWarning = warning.onAccent,
        warningContainer = warning.container,
        onWarningContainer = warning.onContainer,
        neutral = neutral.accent,
        onNeutral = neutral.onAccent,
        neutralContainer = neutral.container,
        onNeutralContainer = neutral.onContainer,
    )
}

internal fun buildMaterialColorScheme(palette: WafflePalette, dark: Boolean): ColorScheme {
    val primary = palette.primary.roleColors(dark)
    val secondary = palette.secondary.roleColors(dark)
    val tertiary = palette.tertiary.roleColors(dark)
    val error = palette.error.roleColors(dark)
    val background = if (dark) palette.darkBackground else palette.lightBackground
    val onSurface = if (dark) palette.onSurfaceDark else palette.onSurfaceLight
    val surface = if (dark) palette.darkSurface else palette.lightSurface
    val surfaceVariant = if (dark) palette.darkSurfaceVariant else palette.lightSurfaceVariant
    val onSurfaceVariant = if (dark) palette.onSurfaceVariantDark else palette.onSurfaceVariantLight
    val outline = if (dark) palette.outlineDark else palette.outlineLight
    val inverseSurface = if (dark) palette.lightBackground else palette.darkSurfaceVariant
    val inverseOnSurface = if (dark) palette.darkBackground else palette.lightBackground

    return if (dark) {
        darkColorScheme(
            primary = primary.accent, onPrimary = primary.onAccent,
            primaryContainer = primary.container, onPrimaryContainer = primary.onContainer,
            secondary = secondary.accent, onSecondary = secondary.onAccent,
            secondaryContainer = secondary.container, onSecondaryContainer = secondary.onContainer,
            tertiary = tertiary.accent, onTertiary = tertiary.onAccent,
            tertiaryContainer = tertiary.container, onTertiaryContainer = tertiary.onContainer,
            error = error.accent, onError = error.onAccent,
            errorContainer = error.container, onErrorContainer = error.onContainer,
            background = background, onBackground = onSurface,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
        )
    } else {
        lightColorScheme(
            primary = primary.accent, onPrimary = primary.onAccent,
            primaryContainer = primary.container, onPrimaryContainer = primary.onContainer,
            secondary = secondary.accent, onSecondary = secondary.onAccent,
            secondaryContainer = secondary.container, onSecondaryContainer = secondary.onContainer,
            tertiary = tertiary.accent, onTertiary = tertiary.onAccent,
            tertiaryContainer = tertiary.container, onTertiaryContainer = tertiary.onContainer,
            error = error.accent, onError = error.onAccent,
            errorContainer = error.container, onErrorContainer = error.onContainer,
            background = background, onBackground = onSurface,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
        )
    }
}

val LocalAppColors = staticCompositionLocalOf { buildAppColors(WafflePalette.Default, dark = false) }
val LocalExtendedColors = staticCompositionLocalOf { buildExtendedColors(WafflePalette.Default, dark = false) }

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: WafflePalette = LocalWafflePalette.current,
    typeScale: AppTypeScale = LocalAppTypeScale.current,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(palette, darkTheme) { buildMaterialColorScheme(palette, darkTheme) }
    val appColors = remember(palette, darkTheme) { buildAppColors(palette, darkTheme) }
    val extendedColors = remember(palette, darkTheme) { buildExtendedColors(palette, darkTheme) }
    val appTokens = remember(appColors) { buildAppTokens(appColors) }
    val typography = remember(typeScale) { buildTypography(typeScale, GeistSans) }

    CompositionLocalProvider(
        LocalWafflePalette provides palette,
        LocalAppTypeScale provides typeScale,
        LocalAppColors provides appColors,
        LocalExtendedColors provides extendedColors,
        LocalAppTokens provides appTokens,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = WaffleHQShapes,
            content = content,
        )
    }
}

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current

    val tokens: AppTokens
        @Composable @ReadOnlyComposable
        get() = LocalAppTokens.current

    val extendedColors: ExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalExtendedColors.current

    val palette: WafflePalette
        @Composable @ReadOnlyComposable
        get() = LocalWafflePalette.current

    val typeScale: AppTypeScale
        @Composable @ReadOnlyComposable
        get() = LocalAppTypeScale.current

    val colorRamps: List<ColorRamp>
        @Composable @ReadOnlyComposable
        get() = LocalWafflePalette.current.ramps
}
