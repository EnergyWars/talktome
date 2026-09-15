package com.wafflehq.talktome.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wafflehq.talktome.R
import com.wafflehq.uikit.components.SettingsDropdownField
import com.wafflehq.uikit.components.SettingsGroup
import com.wafflehq.uikit.components.SettingsScaffold
import com.wafflehq.uikit.theme.AppRole
import com.wafflehq.uikit.theme.ThemeMode

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

@Composable
fun DisplaySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val modes = ThemeMode.entries
    val labels = modes.map { themeModeLabel(it) }

    SettingsScaffold(
        title = stringResource(R.string.settings_display_title),
        onBack = onBack,
        backDescription = stringResource(R.string.label_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SettingsGroup(
                label = stringResource(R.string.settings_group_general),
                tint = AppRole.Primary,
                fraction = 0.08f,
            ) {
                SettingsDropdownField(
                    label = stringResource(R.string.settings_design_label),
                    value = themeModeLabel(themeMode),
                    options = labels,
                    selectedIndex = modes.indexOf(themeMode),
                    onSelect = { index -> viewModel.onThemeModeSelected(modes[index]) },
                )
            }
        }
    }
}
