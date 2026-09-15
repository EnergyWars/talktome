package com.wafflehq.uikit.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

object AppNavigationTestTags {
    const val TOP_BAR = "app_nav_top_bar"
    const val DRAWER = "app_nav_drawer"
    fun topBarTab(label: String) = "app_nav_tab_$label"
    fun drawerItem(label: String) = "app_nav_drawer_item_$label"
}

@Immutable
data class AppNavItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Immutable
data class AppNavSection(
    val label: String? = null,
    val items: List<AppNavItem>,
)

@Composable
fun AppNavigationScaffold(
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    gesturesEnabled: Boolean = drawerState.isOpen,
    content: @Composable (PaddingValues) -> Unit,
) {
    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        drawerContent = drawerContent,
    ) {
        Scaffold(
            topBar = topBar,
            floatingActionButton = floatingActionButton,
            floatingActionButtonPosition = floatingActionButtonPosition,
            content = content,
        )
    }
}

@Composable
fun AppTopNavBar(
    items: List<AppNavItem>,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    colors: AppNavColors = appNavColors(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.topBarBackground)
            .windowInsetsPadding(windowInsets)
            .testTag(AppNavigationTestTags.TOP_BAR),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            items.forEach { item ->
                AppTopNavTab(modifier = Modifier.weight(1f), item = item, colors = colors)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.topBarDivider),
        )
    }
}

@Composable
private fun AppTopNavTab(modifier: Modifier, item: AppNavItem, colors: AppNavColors) {
    val pillColor = if (item.selected) colors.topBarSelectedPillBackground else Color.Transparent
    val iconColor = if (item.selected) colors.topBarSelectedIcon else colors.topBarUnselectedIcon
    val labelColor = if (item.selected) colors.topBarSelectedLabel else colors.topBarUnselectedLabel
    Column(
        modifier = modifier
            .fillMaxHeight()
            .alpha(if (item.enabled) 1f else 0.4f)
            .clickable(enabled = item.enabled, onClick = item.onClick)
            .padding(vertical = 10.dp)
            .testTag(AppNavigationTestTags.topBarTab(item.label)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(pillColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 1,
        )
    }
}

@Composable
fun AppSideNavDrawer(
    title: String,
    sections: List<AppNavSection>,
    modifier: Modifier = Modifier,
    colors: AppNavColors = appNavColors(),
) {
    val pillShape = CircleShape
    val itemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = colors.drawerSelectedContainer,
        unselectedContainerColor = colors.drawerUnselectedContainer,
        selectedIconColor = colors.drawerSelectedIcon,
        unselectedIconColor = colors.drawerUnselectedIcon,
        selectedTextColor = colors.drawerSelectedLabel,
        unselectedTextColor = colors.drawerUnselectedLabel,
    )
    ModalDrawerSheet(
        modifier = modifier.testTag(AppNavigationTestTags.DRAWER),
        drawerContainerColor = colors.drawerBackground,
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.drawerTitle,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            )
            sections.forEach { section ->
                if (section.label != null) {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.drawerSectionLabel,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    )
                }
                section.items.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        selected = item.selected,
                        onClick = item.onClick,
                        shape = pillShape,
                        colors = itemColors,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .then(
                                if (item.selected) {
                                    Modifier.border(1.dp, colors.selectedPill, pillShape)
                                } else {
                                    Modifier
                                },
                            )
                            .testTag(AppNavigationTestTags.drawerItem(item.label)),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
