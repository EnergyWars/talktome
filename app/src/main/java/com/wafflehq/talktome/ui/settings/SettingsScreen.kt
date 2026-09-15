package com.wafflehq.talktome.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wafflehq.talktome.R
import com.wafflehq.uikit.components.SettingsGroupDivider
import com.wafflehq.uikit.components.SettingsListRow
import com.wafflehq.uikit.components.SettingsScaffold

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenGeminiSettings: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenNotes: () -> Unit,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        backDescription = stringResource(R.string.label_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SettingsListRow(
                title = stringResource(R.string.settings_display_title),
                subtitle = stringResource(R.string.settings_display_sub),
                onClick = onOpenDisplay,
            )
            SettingsGroupDivider()
            SettingsListRow(
                title = stringResource(R.string.settings_row_profile),
                subtitle = stringResource(R.string.settings_row_profile_sub),
                onClick = onOpenProfile,
            )
            SettingsGroupDivider()
            SettingsListRow(
                title = stringResource(R.string.settings_row_pairing),
                subtitle = stringResource(R.string.settings_row_pairing_sub),
                onClick = onOpenPairing,
            )
            SettingsGroupDivider()
            SettingsListRow(
                title = stringResource(R.string.settings_row_gemini),
                subtitle = stringResource(R.string.settings_row_gemini_sub),
                onClick = onOpenGeminiSettings,
            )
            SettingsGroupDivider()
            SettingsListRow(
                title = stringResource(R.string.settings_row_notes),
                subtitle = stringResource(R.string.settings_row_notes_sub),
                onClick = onOpenNotes,
            )
        }
    }
}
