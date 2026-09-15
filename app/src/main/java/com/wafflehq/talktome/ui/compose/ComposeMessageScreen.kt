package com.wafflehq.talktome.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wafflehq.talktome.R
import com.wafflehq.talktome.data.db.NegotiationTurnSender
import com.wafflehq.talktome.data.negotiation.OutgoingActionErrorReason
import com.wafflehq.talktome.data.prompts.FriendOpinion
import com.wafflehq.uikit.components.AppBanner
import com.wafflehq.uikit.components.AppButton
import com.wafflehq.uikit.components.AppCard
import com.wafflehq.uikit.components.AppIconButton
import com.wafflehq.uikit.components.ButtonVariant
import com.wafflehq.uikit.components.CardVariant
import com.wafflehq.uikit.components.SettingsScaffold
import com.wafflehq.uikit.textarea.KeyboardAwareTextArea
import com.wafflehq.uikit.theme.AppRole
import com.wafflehq.uikit.theme.AppSpacing

@Composable
fun ComposeMessageScreen(
    onBack: () -> Unit,
    viewModel: ComposeMessageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(R.string.compose_title),
        onBack = onBack,
        backDescription = stringResource(R.string.label_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            if (uiState.sentConfirmation) {
                AppBanner(
                    title = stringResource(R.string.compose_sent_title),
                    body = stringResource(R.string.compose_sent_body),
                    role = AppRole.Success,
                )
            }

            uiState.errorMessage?.let { reason ->
                AppBanner(
                    title = stringResource(R.string.compose_error_title),
                    body = composeErrorBody(reason),
                    role = AppRole.Error,
                )
            }

            val activeMessage = uiState.activeMessage
            if (activeMessage == null) {
                AppBanner(
                    title = stringResource(R.string.compose_intro_title),
                    body = stringResource(R.string.compose_intro_body),
                    role = AppRole.Primary,
                )
                KeyboardAwareTextArea(
                    value = uiState.draftInput,
                    onValueChange = viewModel::onDraftInputChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text(stringResource(R.string.compose_draft_label)) },
                    minLines = 4,
                )
                AppButton(
                    text = stringResource(R.string.compose_action_start),
                    role = AppRole.Primary,
                    variant = ButtonVariant.Tonal,
                    onClick = viewModel::onStartNegotiation,
                    enabled = !uiState.isBusy && uiState.draftInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                if (uiState.isEscalating) {
                    AppBanner(
                        title = stringResource(R.string.compose_escalation_title),
                        body = stringResource(R.string.compose_escalation_body),
                        role = AppRole.Warning,
                    )
                }

                if (uiState.friendOpinions.isNotEmpty()) {
                    FriendOpinionsSection(uiState.friendOpinions)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    contentPadding = PaddingValues(vertical = AppSpacing.sm),
                ) {
                    items(uiState.turns) { turn ->
                        NegotiationBubble(fromUser = turn.sender == NegotiationTurnSender.USER, text = turn.text)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    KeyboardAwareTextArea(
                        value = uiState.replyInput,
                        onValueChange = viewModel::onReplyInputChanged,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.compose_reply_label)) },
                        minLines = 1,
                    )
                    AppIconButton(
                        icon = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(R.string.compose_action_reply),
                        role = AppRole.Primary,
                        enabled = !uiState.isBusy && uiState.replyInput.isNotBlank(),
                        onClick = viewModel::onSendReply,
                    )
                }

                AppButton(
                    text = stringResource(R.string.compose_action_send_now),
                    role = AppRole.Primary,
                    variant = ButtonVariant.Filled,
                    onClick = viewModel::onSendNow,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FriendOpinionsSection(opinions: List<FriendOpinion>) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(stringResource(R.string.compose_opinions_title), style = MaterialTheme.typography.titleSmall)
        opinions.forEach { opinion ->
            AppCard(variant = CardVariant.Outlined, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Text(opinion.label, style = MaterialTheme.typography.titleSmall)
                    Text(opinion.opinion, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun composeErrorBody(reason: OutgoingActionErrorReason): String = when (reason) {
    OutgoingActionErrorReason.NO_API_KEY -> stringResource(R.string.compose_error_no_api_key)
    OutgoingActionErrorReason.INVALID_API_KEY -> stringResource(R.string.compose_error_invalid_api_key)
    OutgoingActionErrorReason.NO_PARTNER -> stringResource(R.string.compose_error_no_partner)
    OutgoingActionErrorReason.NETWORK -> stringResource(R.string.compose_error_network)
    OutgoingActionErrorReason.BLOCKED_BY_SAFETY_FILTER -> stringResource(R.string.compose_error_blocked_by_safety_filter)
    OutgoingActionErrorReason.SERVICE_UNAVAILABLE -> stringResource(R.string.compose_error_service_unavailable)
    OutgoingActionErrorReason.SERVER_ERROR -> stringResource(R.string.compose_error_server)
}

@Composable
private fun NegotiationBubble(fromUser: Boolean, text: String) {
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
