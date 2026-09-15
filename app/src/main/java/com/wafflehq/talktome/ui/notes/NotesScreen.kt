package com.wafflehq.talktome.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.wafflehq.talktome.R
import com.wafflehq.uikit.components.AppBanner
import com.wafflehq.uikit.components.AppButton
import com.wafflehq.uikit.components.AppDialog
import com.wafflehq.uikit.components.ButtonVariant
import com.wafflehq.uikit.components.SettingsScaffold
import com.wafflehq.uikit.theme.AppRole
import com.wafflehq.uikit.theme.AppSpacing

@Composable
fun NotesScreen(
    onBack: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.notes_title),
        onBack = onBack,
        backDescription = stringResource(R.string.label_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
            AppBanner(
                title = stringResource(R.string.notes_intro_title),
                body = stringResource(R.string.notes_intro_body),
                role = AppRole.Primary,
            )
            AppButton(
                text = stringResource(R.string.notes_action_reset),
                role = AppRole.Error,
                variant = ButtonVariant.Tonal,
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showResetDialog) {
        AppDialog(
            onDismissRequest = { showResetDialog = false },
            title = stringResource(R.string.notes_reset_dialog_title),
            text = stringResource(R.string.notes_reset_dialog_body),
            confirmText = stringResource(R.string.notes_action_reset),
            confirmRole = AppRole.Error,
            onConfirm = {
                showResetDialog = false
                viewModel.onResetNotes()
            },
            dismissText = stringResource(R.string.label_cancel),
            onDismiss = { showResetDialog = false },
        )
    }
}
