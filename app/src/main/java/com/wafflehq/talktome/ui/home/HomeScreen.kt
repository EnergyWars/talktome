package com.wafflehq.talktome.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wafflehq.talktome.R
import com.wafflehq.uikit.components.AppBanner
import com.wafflehq.uikit.components.AppScaffold
import com.wafflehq.uikit.components.HeaderItem
import com.wafflehq.uikit.components.SettingsGroupDivider
import com.wafflehq.uikit.components.SettingsListRow
import com.wafflehq.uikit.theme.AppRole
import com.wafflehq.uikit.theme.AppSpacing
import com.wafflehq.uikit.theme.AppTheme

private data class HomeQuickLink(val title: String, val subtitle: String, val onClick: () -> Unit)

@Composable
fun HomeScreen(
    onOpenMenu: () -> Unit,
    onNavigateHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenGeminiSettings: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenCompose: () -> Unit,
    onOpenVenting: () -> Unit,
    onOpenInbox: () -> Unit,
) {
    AppScaffold(
        activeItem = HeaderItem.Home,
        onOpenMenu = onOpenMenu,
        onNavigateHome = onNavigateHome,
        onOpenSettings = onOpenSettings,
    ) { padding ->
        HomeContent(
            padding = padding,
            onOpenProfile = onOpenProfile,
            onOpenGeminiSettings = onOpenGeminiSettings,
            onOpenPairing = onOpenPairing,
            onOpenCompose = onOpenCompose,
            onOpenVenting = onOpenVenting,
            onOpenInbox = onOpenInbox,
        )
    }
}

@Composable
private fun HomeContent(
    padding: PaddingValues,
    onOpenProfile: () -> Unit,
    onOpenGeminiSettings: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenCompose: () -> Unit,
    onOpenVenting: () -> Unit,
    onOpenInbox: () -> Unit,
) {
    val quickLinks = listOf(
        HomeQuickLink(
            title = stringResource(R.string.settings_row_compose),
            subtitle = stringResource(R.string.settings_row_compose_sub),
            onClick = onOpenCompose,
        ),
        HomeQuickLink(
            title = stringResource(R.string.settings_row_venting),
            subtitle = stringResource(R.string.settings_row_venting_sub),
            onClick = onOpenVenting,
        ),
        HomeQuickLink(
            title = stringResource(R.string.settings_row_inbox),
            subtitle = stringResource(R.string.settings_row_inbox_sub),
            onClick = onOpenInbox,
        ),
        HomeQuickLink(
            title = stringResource(R.string.settings_row_profile),
            subtitle = stringResource(R.string.settings_row_profile_sub),
            onClick = onOpenProfile,
        ),
        HomeQuickLink(
            title = stringResource(R.string.settings_row_pairing),
            subtitle = stringResource(R.string.settings_row_pairing_sub),
            onClick = onOpenPairing,
        ),
        HomeQuickLink(
            title = stringResource(R.string.settings_row_gemini),
            subtitle = stringResource(R.string.settings_row_gemini_sub),
            onClick = onOpenGeminiSettings,
        ),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        item {
            AppBanner(
                title = stringResource(R.string.home_intro_title),
                body = stringResource(R.string.home_intro_body),
                role = AppRole.Primary,
            )
        }
        item {
            Column(Modifier.background(AppTheme.colors.surface)) {
                quickLinks.forEachIndexed { index, link ->
                    SettingsListRow(title = link.title, subtitle = link.subtitle, onClick = link.onClick)
                    if (index < quickLinks.lastIndex) SettingsGroupDivider()
                }
            }
        }
    }
}
