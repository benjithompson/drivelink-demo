package com.drivelink.demo.carcare

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.DlAccentButton
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.DlCard
import com.drivelink.core.designsystem.component.ErrorState
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.format.displayMessageWithId
import com.drivelink.core.domain.model.ServiceItemStatus

/** Car Care tab. [onScheduleService] opens Schedule Service. The top bar (title "Car Care") comes from the app shell. */
@Composable
fun CarCareRoute(
    onScheduleService: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarCareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onShown() }
    CarCareScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onScheduleService = onScheduleService,
        onDial = { dial(context, it) },
        modifier = modifier,
    )
}

/** Opens the phone app with [number] typed in. It does not start a call. */
private fun dial(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    } catch (_: ActivityNotFoundException) {
        // A device without a phone app: nothing to open.
    }
}

/**
 * The Car Care layout of the Phase 1 mock, with live data.
 * Tags: `screen_carcare`, `carcare_refresh`, `carcare_loading`, `carcare_headline`, `carcare_summary`,
 * `carcare_odometer`, `carcare_last_miles`, `carcare_last_date`, `carcare_next_miles`, `carcare_next_date`,
 * `carcare_interval`, `maint_item_<id>`, `maint_status_<id>`, `recall_<id>`, `recall_status_<id>`, `recalls_none`,
 * `schedule_service`, `service_center`, `service_center_name`, `service_center_address`, `service_center_phone`,
 * `service_center_distance`, `service_center_hours`, `service_center_open`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarCareScreen(
    state: CarCareUiState,
    onRefresh: () -> Unit,
    onScheduleService: () -> Unit,
    onDial: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().testTag("carcare_refresh"),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("screen_carcare"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val content = state.content
            when {
                content != null -> {
                    state.error?.let { ReloadBanner(it, onRefresh) }
                    CarCareContentView(content, onScheduleService, onDial)
                }
                state.error != null -> ErrorState(
                    message = state.error.displayMessage(),
                    correlationId = state.error.correlationId,
                    onRetry = onRefresh,
                    title = "Couldn't load Car Care",
                )
                else -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.brand, modifier = Modifier.testTag("carcare_loading"))
                }
            }
            // The scenario chip (bottom left, drawn by the app shell) must cover no text.
            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun ReloadBanner(error: AppError, onRetry: () -> Unit) {
    DlBanner(
        BannerKind.Error,
        error.displayMessageWithId(),
        actionLabel = "Retry",
        onAction = onRetry,
    )
}

@Composable
private fun CarCareContentView(content: CarCareContent, onScheduleService: () -> Unit, onDial: (String) -> Unit) {
    val c = DlTheme.colors
    val (icon, tint) = when (content.headline) {
        CarCareHeadline.Good -> Icons.Filled.CheckCircle to c.successStrong
        CarCareHeadline.Overdue -> Icons.Filled.ErrorOutline to c.error
        CarCareHeadline.Due, CarCareHeadline.Recall -> Icons.Filled.Warning to c.warning
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                content.headline.text,
                style = MaterialTheme.typography.titleLarge,
                color = when (content.headline) {
                    CarCareHeadline.Good -> c.successStrong
                    CarCareHeadline.Overdue -> c.error
                    else -> c.textPrimary
                },
                modifier = Modifier.testTag("carcare_headline").semantics { heading() },
            )
        }
        content.summary?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(start = 28.dp).testTag("carcare_summary"),
            )
        }
    }
    DlCard {
        Column {
            Figures("Odometer (Estimated)", content.odometer, "carcare_odometer", null, null, null)
            HorizontalDivider(color = c.divider)
            Figures("Last Service Completed", content.lastMiles, "carcare_last_miles", content.lastDate, "carcare_last_date", null)
            HorizontalDivider(color = c.divider)
            Figures("Next Service Due At", content.nextMiles, "carcare_next_miles", content.nextDate, "carcare_next_date", c.accent)
        }
    }
    Text("Maintenance Schedule", style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
    content.intervalText?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.testTag("carcare_interval"))
    }
    if (content.items.isNotEmpty()) {
        Column {
            content.items.forEachIndexed { i, item ->
                MaintenanceRow(item, showDivider = i < content.items.lastIndex)
            }
        }
    }
    RecallSection(content.recalls)
    DlAccentButton("Schedule Service", onScheduleService, Modifier.fillMaxWidth().testTag("schedule_service"))
    content.center?.let { ServiceCenterCard(it, onDial) }
}

/** Two figures side by side: a value with a label on the left, an optional value on the right (the date). */
@Composable
private fun Figures(
    label: String,
    value: String?,
    tag: String,
    second: String?,
    secondTag: String?,
    labelColor: Color?,
) {
    val c = DlTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = labelColor ?: c.textSecondary)
            Text(
                value ?: "—",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal),
                color = c.textPrimary,
                modifier = Modifier.testTag(tag),
            )
        }
        Column(Modifier.weight(1f)) {
            if (secondTag != null) {
                Text(" ", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clearAndSetSemantics {})
                Text(
                    second ?: "—",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal),
                    color = c.textPrimary,
                    modifier = Modifier.testTag(secondTag),
                )
            }
        }
    }
}

@Composable
private fun MaintenanceRow(item: MaintenanceItemUi, showDivider: Boolean) {
    val c = DlTheme.colors
    Column(Modifier.fillMaxWidth().testTag("maint_item_${item.id}")) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                item.due?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
            }
            Spacer(Modifier.width(12.dp))
            StatusTag(
                item.statusLabel, item.status.tint(), Modifier.testTag("maint_status_${item.id}"),
                alert = item.status == ServiceItemStatus.OVERDUE,
            )
        }
        if (showDivider) HorizontalDivider(color = c.divider)
    }
}

@Composable
private fun ServiceItemStatus.tint(): Color = when (this) {
    ServiceItemStatus.UPCOMING -> DlTheme.colors.textSecondary
    ServiceItemStatus.DUE -> DlTheme.colors.warning
    ServiceItemStatus.OVERDUE -> DlTheme.colors.error
}

/** Small pill with a tinted background. The text keeps a high-contrast color. */
@Composable
private fun StatusTag(text: String, tint: Color, modifier: Modifier = Modifier, alert: Boolean = false) {
    val c = DlTheme.colors
    Text(
        text,
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (alert) c.error else c.textPrimary,
    )
}

@Composable
private fun RecallSection(recalls: List<RecallUi>) {
    val c = DlTheme.colors
    Text(
        "Recalls",
        style = MaterialTheme.typography.titleMedium,
        color = c.textPrimary,
        modifier = Modifier.semantics { heading() }.testTag("recalls_section"),
    )
    if (recalls.isEmpty()) {
        Text("No open recalls", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.testTag("recalls_none"))
        return
    }
    recalls.forEach { recall ->
        DlCard(Modifier.testTag("recall_${recall.id}")) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(recall.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    StatusTag(
                        if (recall.open) "Open" else "Remedied",
                        if (recall.open) c.error else c.successStrong,
                        Modifier.testTag("recall_status_${recall.id}"),
                        alert = recall.open,
                    )
                }
                Text(
                    "Campaign ${recall.campaign}" + (recall.issued?.let { " · Issued $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                recall.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary) }
            }
        }
    }
}

@Composable
private fun ServiceCenterCard(center: ServiceCenterUi, onDial: (String) -> Unit) {
    val c = DlTheme.colors
    DlCard(Modifier.testTag("service_center"), color = c.accentContainer) {
        Column {
            Text(center.name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.testTag("service_center_name"))
            Text("Preferred Service Center", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(10.dp))
            Text(center.address, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.testTag("service_center_address"))
            if (center.phone != null) {
                val dialNumber = center.dialNumber
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .then(
                            if (dialNumber != null) {
                                Modifier.clickable(onClickLabel = "Call", role = Role.Button) { onDial(dialNumber) }
                            } else {
                                Modifier
                            },
                        )
                        .semantics { contentDescription = "Phone ${center.phone}" }
                        .testTag("service_center_phone"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = c.brand, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(center.phone, style = MaterialTheme.typography.bodyMedium, color = c.brand)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    if (center.distance != null) {
                        Text("Distance", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Text(center.distance, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.testTag("service_center_distance"))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    center.hours?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.testTag("service_center_hours"))
                    }
                    center.openNow?.let {
                        Text(
                            if (it) "Open" else "Closed",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (it) c.successStrong else c.error,
                            modifier = Modifier.testTag("service_center_open"),
                        )
                    }
                }
            }
        }
    }
}
