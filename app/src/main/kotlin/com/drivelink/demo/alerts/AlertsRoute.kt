package com.drivelink.demo.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.ErrorState
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.format.displayMessageWithId
import com.drivelink.core.domain.model.AlertSeverity

/** Alerts sub-screen (bell in the Home top bar). The top bar comes from the app shell. */
@Composable
fun AlertsRoute(modifier: Modifier = Modifier, viewModel: AlertsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onShown() }
    AlertsScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onMarkRead = viewModel::markRead,
        onMarkAllRead = viewModel::markAllRead,
        onDismissMarkError = viewModel::dismissMarkError,
        modifier = modifier,
    )
}

/**
 * Tags: `screen_alerts`, `alerts_refresh`, `alerts_loading`, `alerts_empty`, `alerts_summary`, `alerts_mark_all`,
 * `alert_row_<id>`, `alert_unread_<id>` (present only while the alert is unread), `alert_title_<id>`,
 * `alert_body_<id>`, `alert_time_<id>`, `alert_vehicle_<id>`, `alert_severity_<id>`, and the error tags of `ErrorState`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    state: AlertsUiState,
    onRefresh: () -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDismissMarkError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().testTag("alerts_refresh"),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("screen_alerts"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.hasData -> {
                    state.error?.let {
                        DlBanner(
                            BannerKind.Error,
                            it.displayMessageWithId(),
                            actionLabel = "Retry",
                            onAction = onRefresh,
                        )
                    }
                    state.markError?.let {
                        DlBanner(
                            BannerKind.Error,
                            "Could not mark the alert as read. " + it.message + (it.correlationId?.let { id -> " (ID $id)" } ?: ""),
                            actionLabel = "Dismiss",
                            onAction = onDismissMarkError,
                        )
                    }
                    if (state.empty) {
                        Text(
                            "No alerts",
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = 8.dp).testTag("alerts_empty"),
                        )
                    } else {
                        Header(state.unreadCount, onMarkAllRead)
                        Column {
                            state.rows.forEachIndexed { i, row ->
                                AlertRow(row, onClick = { onMarkRead(row.id) })
                                if (i < state.rows.lastIndex) HorizontalDivider(color = c.divider)
                            }
                        }
                    }
                }
                state.error != null -> ErrorState(
                    message = state.error.displayMessage(),
                    correlationId = state.error.correlationId,
                    onRetry = onRefresh,
                    title = "Couldn't load alerts",
                )
                else -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.brand, modifier = Modifier.testTag("alerts_loading"))
                }
            }
            // The scenario chip (bottom left, drawn by the app shell) must cover no text.
            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun Header(unread: Int, onMarkAllRead: () -> Unit) {
    val c = DlTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (unread == 0) "All caught up" else if (unread == 1) "1 unread" else "$unread unread",
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            modifier = Modifier.weight(1f).testTag("alerts_summary"),
        )
        if (unread > 0) {
            TextButton(onClick = onMarkAllRead, modifier = Modifier.testTag("alerts_mark_all")) {
                Text("Mark all read", color = c.brand)
            }
        }
    }
}

@Composable
private fun AlertRow(row: AlertRowUi, onClick: () -> Unit) {
    val c = DlTheme.colors
    val (icon, tint) = row.severity.look()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClickLabel = if (row.unread) "Mark as read" else null, onClick = onClick)
            .semantics { stateDescription = if (row.unread) "Unread" else "Read" }
            .padding(vertical = 14.dp)
            .testTag("alert_row_${row.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = row.severity.label(),
            tint = tint,
            modifier = Modifier.size(24.dp).testTag("alert_severity_${row.id}"),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (row.unread) FontWeight.Bold else FontWeight.Normal,
                color = c.textPrimary,
                modifier = Modifier.testTag("alert_title_${row.id}"),
            )
            Text(row.body, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.testTag("alert_body_${row.id}"))
            Spacer(Modifier.height(4.dp))
            Row {
                if (row.timeAgo.isNotEmpty()) {
                    Text(row.timeAgo, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.testTag("alert_time_${row.id}"))
                }
                row.vehicle?.let {
                    Text(
                        (if (row.timeAgo.isNotEmpty()) " · " else "") + it,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.testTag("alert_vehicle_${row.id}"),
                    )
                }
            }
        }
        if (row.unread) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .background(c.accent, CircleShape)
                    .testTag("alert_unread_${row.id}"),
            )
        }
    }
}

@Composable
private fun AlertSeverity.look(): Pair<ImageVector, androidx.compose.ui.graphics.Color> {
    val c = DlTheme.colors
    return when (this) {
        AlertSeverity.INFO -> Icons.Filled.Info to c.accent
        AlertSeverity.WARN -> Icons.Filled.WarningAmber to c.warning
        AlertSeverity.CRITICAL -> Icons.Filled.ErrorOutline to c.error
    }
}

private fun AlertSeverity.label(): String = when (this) {
    AlertSeverity.INFO -> "Information"
    AlertSeverity.WARN -> "Warning"
    AlertSeverity.CRITICAL -> "Critical"
}
