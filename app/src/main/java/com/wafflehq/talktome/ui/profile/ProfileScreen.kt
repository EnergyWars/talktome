package com.wafflehq.talktome.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wafflehq.talktome.R
import com.wafflehq.uikit.components.SettingsGroup
import com.wafflehq.uikit.components.SettingsScaffold
import com.wafflehq.uikit.textarea.KeyboardAwareTextArea
import com.wafflehq.uikit.theme.AppRadius
import com.wafflehq.uikit.theme.AppRole
import com.wafflehq.uikit.theme.AppSpacing
import com.wafflehq.uikit.theme.AppTheme

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(R.string.settings_row_profile),
        onBack = onBack,
        backDescription = stringResource(R.string.label_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            SettingsGroup(
                label = stringResource(R.string.profile_group_self),
                tint = AppRole.Primary,
                fraction = 0.08f,
            ) {
                ProfileTextArea(
                    value = profile.selfDescription,
                    onValueChange = viewModel::onSelfDescriptionChanged,
                    label = stringResource(R.string.profile_self_label),
                    supportingText = stringResource(R.string.profile_self_help),
                )
            }
            SettingsGroup(
                label = stringResource(R.string.profile_group_partner),
                tint = AppRole.Secondary,
                fraction = 0.08f,
            ) {
                ProfileTextArea(
                    value = profile.partnerDescription,
                    onValueChange = viewModel::onPartnerDescriptionChanged,
                    label = stringResource(R.string.profile_partner_label),
                    supportingText = stringResource(R.string.profile_partner_help),
                )
            }
            SettingsGroup(
                label = stringResource(R.string.profile_group_filter),
                tint = AppRole.Tertiary,
                fraction = 0.09f,
            ) {
                ProfileTextArea(
                    value = profile.filterText,
                    onValueChange = viewModel::onFilterTextChanged,
                    label = stringResource(R.string.profile_filter_label),
                    supportingText = stringResource(R.string.profile_filter_help),
                )
            }
        }
    }
}

@Composable
private fun ProfileTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
) {
    KeyboardAwareTextArea(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
        label = { Text(label) },
        supportingText = { Text(supportingText, style = MaterialTheme.typography.bodySmall) },
        minLines = 3,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AppTheme.colors.surface,
            unfocusedContainerColor = AppTheme.colors.surface,
            focusedBorderColor = AppTheme.colors.primary.accent,
            unfocusedBorderColor = AppTheme.colors.outline,
            focusedLabelColor = AppTheme.colors.primary.accent,
            unfocusedLabelColor = AppTheme.colors.onSurfaceVariant,
        ),
        shape = RoundedCornerShape(AppRadius.textField),
    )
}
