package com.wafflehq.talktome.ui.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import android.content.ClipData
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wafflehq.talktome.R
import com.wafflehq.talktome.data.pairing.PairingErrorReason
import com.wafflehq.uikit.components.AppBanner
import com.wafflehq.uikit.components.AppButton
import com.wafflehq.uikit.components.AppCard
import com.wafflehq.uikit.components.AppDialog
import com.wafflehq.uikit.components.AppIconButton
import com.wafflehq.uikit.components.AppTextField
import com.wafflehq.uikit.components.ButtonVariant
import com.wafflehq.uikit.components.CardVariant
import com.wafflehq.uikit.components.SettingsScaffold
import com.wafflehq.uikit.textarea.KeyboardAwareTextArea
import com.wafflehq.uikit.theme.AppRole
import com.wafflehq.uikit.theme.AppSpacing
import kotlinx.coroutines.launch

@Composable
fun PairingScreen(
    onBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val partner by viewModel.partner.collectAsStateWithLifecycle()
    var showUnpairDialog by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.pairing_title),
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
            val currentPartner = partner
            if (currentPartner != null) {
                AppCard(variant = CardVariant.Outlined, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    ) {
                        Text(stringResource(R.string.pairing_connected_title), style = MaterialTheme.typography.titleMedium)
                        Text(currentPartner.displayName, style = MaterialTheme.typography.bodyMedium)
                        AppButton(
                            text = stringResource(R.string.pairing_action_disconnect),
                            role = AppRole.Error,
                            variant = ButtonVariant.Text,
                            onClick = { showUnpairDialog = true },
                        )
                    }
                }
            } else {
                AppBanner(
                    title = stringResource(R.string.pairing_intro_title),
                    body = stringResource(R.string.pairing_intro_body),
                    role = AppRole.Primary,
                )

                AppTextField(
                    value = uiState.displayName,
                    onValueChange = viewModel::onDisplayNameChanged,
                    label = stringResource(R.string.pairing_display_name_label),
                    role = AppRole.Primary,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                AppTextField(
                    value = uiState.serverBaseUrl,
                    onValueChange = viewModel::onServerBaseUrlChanged,
                    label = stringResource(R.string.pairing_server_url_label),
                    role = AppRole.Primary,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                uiState.errorMessage?.let { reason ->
                    AppBanner(
                        title = stringResource(R.string.pairing_error_title),
                        body = pairingErrorBody(reason),
                        role = AppRole.Error,
                    )
                }

                CreateInviteSection(uiState = uiState, onCreateInvite = viewModel::onCreateInvite)
                AcceptInviteSection(
                    enteredCode = uiState.enteredCode,
                    isBusy = uiState.isBusy,
                    onEnteredCodeChanged = viewModel::onEnteredCodeChanged,
                    onAcceptInvite = viewModel::onAcceptInvite,
                )
            }
        }
    }

    if (showUnpairDialog) {
        AppDialog(
            onDismissRequest = { showUnpairDialog = false },
            title = stringResource(R.string.pairing_disconnect_dialog_title),
            text = stringResource(R.string.pairing_disconnect_dialog_body),
            confirmText = stringResource(R.string.pairing_action_disconnect),
            confirmRole = AppRole.Error,
            onConfirm = {
                showUnpairDialog = false
                viewModel.onUnpair()
            },
            dismissText = stringResource(R.string.label_cancel),
            onDismiss = { showUnpairDialog = false },
        )
    }
}

@Composable
private fun CreateInviteSection(
    uiState: PairingUiState,
    onCreateInvite: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(stringResource(R.string.pairing_create_invite_title), style = MaterialTheme.typography.titleSmall)
        AppButton(
            text = stringResource(R.string.pairing_action_create_invite),
            role = AppRole.Primary,
            variant = ButtonVariant.Tonal,
            onClick = onCreateInvite,
            enabled = !uiState.isBusy,
            modifier = Modifier.fillMaxWidth(),
        )

        uiState.inviteCode?.let { code ->
            val qrBitmap = remember(code) { QrCodeGenerator.generate(code) }
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.pairing_qr_content_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .size(240.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                SelectionContainer {
                    Text(code, style = MaterialTheme.typography.bodySmall)
                }
                val copyCodeLabel = stringResource(R.string.pairing_action_copy_code)
                AppIconButton(
                    icon = Icons.Outlined.ContentCopy,
                    contentDescription = copyCodeLabel,
                    role = AppRole.Neutral,
                    onClick = {
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyCodeLabel, code)))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AcceptInviteSection(
    enteredCode: String,
    isBusy: Boolean,
    onEnteredCodeChanged: (String) -> Unit,
    onAcceptInvite: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(stringResource(R.string.pairing_accept_invite_title), style = MaterialTheme.typography.titleSmall)
        KeyboardAwareTextArea(
            value = enteredCode,
            onValueChange = onEnteredCodeChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp),
            label = { Text(stringResource(R.string.pairing_enter_code_label)) },
            minLines = 3,
        )
        AppButton(
            text = stringResource(R.string.pairing_action_accept_invite),
            role = AppRole.Primary,
            variant = ButtonVariant.Outlined,
            onClick = onAcceptInvite,
            enabled = !isBusy && enteredCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun pairingErrorBody(reason: PairingErrorReason): String = when (reason) {
    PairingErrorReason.NETWORK -> stringResource(R.string.pairing_error_network)
    PairingErrorReason.INVALID_CODE -> stringResource(R.string.pairing_error_invalid_code)
    PairingErrorReason.SERVER_ERROR -> stringResource(R.string.pairing_error_server)
}
