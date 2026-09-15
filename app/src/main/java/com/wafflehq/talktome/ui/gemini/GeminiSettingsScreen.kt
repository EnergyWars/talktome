package com.wafflehq.talktome.ui.gemini

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wafflehq.talktome.R
import com.wafflehq.talktome.data.gemini.GeminiConnectionResult
import com.wafflehq.uikit.components.AppBanner
import com.wafflehq.uikit.components.AppButton
import com.wafflehq.uikit.components.AppDialog
import com.wafflehq.uikit.components.AppIconButton
import com.wafflehq.uikit.components.AppTextField
import com.wafflehq.uikit.components.ButtonVariant
import com.wafflehq.uikit.components.SettingsScaffold
import com.wafflehq.uikit.theme.AppRole
import com.wafflehq.uikit.theme.AppSpacing

@Composable
fun GeminiSettingsScreen(
    onBack: () -> Unit,
    viewModel: GeminiSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.settings_row_gemini),
        onBack = onBack,
        backDescription = stringResource(R.string.label_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
            AppBanner(
                title = stringResource(R.string.gemini_privacy_title),
                body = stringResource(R.string.gemini_privacy_body),
                role = AppRole.Primary,
            )

            if (hasApiKey) {
                AppBanner(
                    title = stringResource(R.string.gemini_key_set_title),
                    body = stringResource(R.string.gemini_key_set_body),
                    role = AppRole.Success,
                )
            }

            AppTextField(
                value = uiState.apiKeyInput,
                onValueChange = viewModel::onApiKeyInputChanged,
                label = stringResource(R.string.gemini_key_label),
                role = AppRole.Primary,
                singleLine = true,
                visualTransformation = if (uiState.isKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    AppIconButton(
                        icon = if (uiState.isKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.gemini_key_toggle_visibility),
                        role = AppRole.Neutral,
                        onClick = viewModel::onToggleVisibility,
                    )
                },
                supportingText = connectionSupportingText(uiState.connectionState),
                isError = uiState.connectionState is GeminiConnectionUiState.Failed,
                modifier = Modifier.fillMaxWidth(),
            )

            when (val connectionState = uiState.connectionState) {
                GeminiConnectionUiState.Success -> AppBanner(
                    title = stringResource(R.string.gemini_test_success_title),
                    body = stringResource(R.string.gemini_test_success_body),
                    role = AppRole.Success,
                    icon = Icons.Outlined.CheckCircle,
                )
                is GeminiConnectionUiState.Failed -> AppBanner(
                    title = stringResource(R.string.gemini_test_failed_title),
                    body = connectionErrorBody(connectionState.result),
                    role = AppRole.Error,
                    icon = Icons.Outlined.Error,
                )
                else -> Unit
            }

            AppButton(
                text = stringResource(R.string.gemini_action_save_and_test),
                role = AppRole.Primary,
                variant = ButtonVariant.Tonal,
                onClick = viewModel::onSaveAndTest,
                enabled = uiState.connectionState != GeminiConnectionUiState.Testing,
                modifier = Modifier.fillMaxWidth(),
            )

            if (hasApiKey) {
                AppButton(
                    text = stringResource(R.string.gemini_action_remove_key),
                    role = AppRole.Error,
                    variant = ButtonVariant.Text,
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showClearDialog) {
        AppDialog(
            onDismissRequest = { showClearDialog = false },
            title = stringResource(R.string.gemini_remove_dialog_title),
            text = stringResource(R.string.gemini_remove_dialog_body),
            confirmText = stringResource(R.string.gemini_action_remove_key),
            confirmRole = AppRole.Error,
            onConfirm = {
                showClearDialog = false
                viewModel.onClearApiKey()
            },
            dismissText = stringResource(R.string.label_cancel),
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun connectionSupportingText(state: GeminiConnectionUiState): String? = when (state) {
    GeminiConnectionUiState.Testing -> stringResource(R.string.gemini_test_in_progress)
    else -> null
}

@Composable
private fun connectionErrorBody(result: GeminiConnectionResult): String = when (result) {
    GeminiConnectionResult.InvalidApiKey -> stringResource(R.string.gemini_error_invalid_key)
    GeminiConnectionResult.RateLimited -> stringResource(R.string.gemini_error_rate_limited)
    GeminiConnectionResult.NetworkError -> stringResource(R.string.gemini_error_network)
    is GeminiConnectionResult.UnknownError -> stringResource(R.string.gemini_error_unknown)
    GeminiConnectionResult.Success -> ""
}
