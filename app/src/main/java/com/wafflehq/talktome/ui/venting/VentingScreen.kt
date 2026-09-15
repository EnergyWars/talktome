package com.wafflehq.talktome.ui.venting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wafflehq.talktome.R
import com.wafflehq.uikit.components.AppBanner
import com.wafflehq.uikit.components.AppButton
import com.wafflehq.uikit.components.AppCard
import com.wafflehq.uikit.components.AppIconButton
import com.wafflehq.uikit.components.AppTextField
import com.wafflehq.uikit.components.ButtonVariant
import com.wafflehq.uikit.components.CardVariant
import com.wafflehq.uikit.components.SettingsScaffold
import com.wafflehq.uikit.theme.AppRole
import com.wafflehq.uikit.theme.AppSpacing
import kotlinx.coroutines.launch

@Composable
fun VentingScreen(
    onBack: () -> Unit,
    onSwitchToCompose: () -> Unit,
    viewModel: VentingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    SettingsScaffold(
        title = stringResource(R.string.venting_title),
        onBack = {
            scope.launch { viewModel.onEndSession(onBack) }
        },
        backDescription = stringResource(R.string.label_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            if (uiState.turns.isEmpty()) {
                AppBanner(
                    title = stringResource(R.string.venting_privacy_title),
                    body = stringResource(R.string.venting_privacy_body),
                    role = AppRole.Primary,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                contentPadding = PaddingValues(vertical = AppSpacing.sm),
            ) {
                itemsIndexed(uiState.turns) { _, turn ->
                    VentingBubble(fromUser = turn.fromUser, text = turn.text)
                }
            }

            uiState.errorMessage?.let { reason ->
                AppBanner(
                    title = stringResource(R.string.venting_error_title),
                    body = when (reason) {
                        VentingErrorReason.NO_API_KEY -> stringResource(R.string.venting_error_no_api_key)
                        VentingErrorReason.NETWORK -> stringResource(R.string.venting_error_network)
                    },
                    role = AppRole.Error,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                AppTextField(
                    value = uiState.input,
                    onValueChange = viewModel::onInputChanged,
                    label = stringResource(R.string.venting_input_label),
                    role = AppRole.Primary,
                    singleLine = false,
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(
                    icon = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = stringResource(R.string.venting_action_send),
                    role = AppRole.Primary,
                    enabled = !uiState.isSending && uiState.input.isNotBlank(),
                    onClick = viewModel::onSend,
                )
            }

            AppButton(
                text = stringResource(R.string.venting_action_switch_to_compose),
                role = AppRole.Neutral,
                variant = ButtonVariant.Text,
                onClick = onSwitchToCompose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VentingBubble(fromUser: Boolean, text: String) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AppCard(
            role = if (fromUser) AppRole.Primary else null,
            variant = if (fromUser) CardVariant.Filled else CardVariant.Outlined,
            modifier = Modifier
                .align(if (fromUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 320.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(AppSpacing.md),
            )
        }
    }
}
