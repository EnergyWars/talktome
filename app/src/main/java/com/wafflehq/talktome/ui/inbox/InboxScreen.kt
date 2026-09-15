package com.wafflehq.talktome.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wafflehq.talktome.R
import com.wafflehq.talktome.data.db.InboxMessageEntity
import com.wafflehq.uikit.components.AppButton
import com.wafflehq.uikit.components.AppCard
import com.wafflehq.uikit.components.ButtonVariant
import com.wafflehq.uikit.components.CardVariant
import com.wafflehq.uikit.components.SettingsScaffold
import com.wafflehq.uikit.theme.AppRole
import com.wafflehq.uikit.theme.AppSpacing
import com.wafflehq.uikit.theme.AppTheme

@Composable
fun InboxScreen(
    onBack: () -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(R.string.inbox_title),
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
            AppButton(
                text = stringResource(if (isSyncing) R.string.inbox_action_refreshing else R.string.inbox_action_refresh),
                role = AppRole.Primary,
                variant = ButtonVariant.Outlined,
                onClick = viewModel::onRefresh,
                enabled = !isSyncing,
                modifier = Modifier.fillMaxWidth(),
            )

            if (messages.isEmpty()) {
                InboxEmptyState()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    items(messages) { message -> InboxMessageCard(message) }
                }
            }
        }
    }
}

@Composable
private fun InboxEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Outlined.MarkEmailRead,
            contentDescription = null,
            tint = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = stringResource(R.string.inbox_empty_title),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InboxMessageCard(message: InboxMessageEntity) {
    AppCard(variant = CardVariant.Outlined, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(message.content, style = MaterialTheme.typography.bodyMedium)
            message.mediatorFeedback?.let { feedback ->
                Text(feedback, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.onSurfaceVariant)
            }
        }
    }
}
