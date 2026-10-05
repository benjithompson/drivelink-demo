package com.drivelink.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivelink.core.designsystem.theme.DlTheme

/** DriveLink wordmark: original mark, a link glyph plus the name. */
@Composable
fun DriveLinkMark(modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(22.dp)
                .background(c.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("D", color = c.onTopBar, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "DriveLink",
            color = c.onTopBar,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        )
    }
}

/** Tab bar for tabs other than Home: centered title, no actions. */
@Composable
fun DlTitleTopBar(title: String, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .background(c.topBar)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = c.onTopBar, style = MaterialTheme.typography.titleMedium)
    }
}

/** Home bar: wordmark on the left, alerts bell and chat on the right. */
@Composable
fun DlTopBar(
    alertCount: Int,
    onAlerts: () -> Unit,
    onChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.topBar)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DriveLinkMark()
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onAlerts, modifier = Modifier.testTag("topbar_alerts")) {
            Box {
                Icon(Icons.Outlined.Notifications, contentDescription = "Alerts", tint = c.onTopBar)
                if (alertCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
                            .background(c.accent, CircleShape)
                            .semantics { contentDescription = "$alertCount unread alerts" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$alertCount", color = c.onTopBar, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        IconButton(onClick = onChat, modifier = Modifier.testTag("topbar_chat")) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Messages", tint = c.onTopBar)
        }
    }
}

/** Sub-screen bar: back chevron and a centered title. */
@Composable
fun DlSubTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = DlTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .background(c.topBar)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).testTag("topbar_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "Back", tint = c.onTopBar)
        }
        Text(
            title,
            modifier = Modifier.align(Alignment.Center),
            color = c.onTopBar,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(Modifier.align(Alignment.CenterEnd), content = actions)
    }
}

data class DlNavItem(val key: String, val label: String, val icon: ImageVector)

/** Bottom navigation: white bar, navy active item, gray inactive items, labels under icons. */
@Composable
fun DlBottomBar(
    items: List<DlNavItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    Column(modifier.background(c.navBar)) {
        HorizontalDivider(color = c.divider)
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val tint = if (selected) c.brand else c.iconInactive
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(item.key) }
                        .padding(vertical = 6.dp)
                        .testTag("tab_${item.key}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        color = tint,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
