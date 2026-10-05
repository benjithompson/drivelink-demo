package com.drivelink.demo.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.DlCard
import com.drivelink.core.designsystem.component.ErrorState
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.format.displayMessageWithId

/** Trips sub-screen (from the Home Trips row). The top bar comes from the app shell. */
@Composable
fun TripsRoute(modifier: Modifier = Modifier, viewModel: TripsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onShown() }
    TripsScreen(state, onRefresh = viewModel::refresh, modifier = modifier)
}

/**
 * Tags: `screen_trips`, `trips_refresh`, `trips_loading`, `trips_empty`, `trips_summary`, `trips_summary_count`,
 * `trips_summary_distance`, `trip_day_<yyyy-MM-dd>`, `trip_row_<id>`, `trip_time_<id>`, `trip_duration_<id>`,
 * `trip_distance_<id>`, `trip_efficiency_<id>`, and the error tags of `ErrorState`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(state: TripsUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().testTag("trips_refresh"),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("screen_trips"),
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
                    Summary(state.summary!!)
                    if (state.empty) {
                        Text(
                            "No trips yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = 8.dp).testTag("trips_empty"),
                        )
                    }
                    state.days.forEach { day -> Day(day) }
                }
                state.error != null -> ErrorState(
                    message = state.error.displayMessage(),
                    correlationId = state.error.correlationId,
                    onRetry = onRefresh,
                    title = "Couldn't load trips",
                )
                else -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.brand, modifier = Modifier.testTag("trips_loading"))
                }
            }
            // The scenario chip (bottom left, drawn by the app shell) must cover no text.
            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun Summary(summary: TripsSummaryUi) {
    val c = DlTheme.colors
    DlCard(Modifier.testTag("trips_summary")) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Trips", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Text(
                    summary.countText,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal),
                    color = c.textPrimary,
                    modifier = Modifier.testTag("trips_summary_count"),
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Total distance", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Text(
                    summary.distanceText,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal),
                    color = c.textPrimary,
                    modifier = Modifier.testTag("trips_summary_distance"),
                )
            }
        }
    }
}

@Composable
private fun Day(day: TripDayUi) {
    val c = DlTheme.colors
    Text(
        day.title,
        style = MaterialTheme.typography.titleMedium,
        color = c.textPrimary,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() }
            .testTag("trip_day_${day.key}"),
    )
    Column {
        day.trips.forEachIndexed { i, trip ->
            TripRow(trip)
            if (i < day.trips.lastIndex) HorizontalDivider(color = c.divider)
        }
    }
}

@Composable
private fun TripRow(trip: TripRowUi) {
    val c = DlTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("trip_row_${trip.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(trip.time, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.testTag("trip_time_${trip.id}"))
            Text(trip.duration, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.testTag("trip_duration_${trip.id}"))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(trip.distance, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.testTag("trip_distance_${trip.id}"))
            trip.efficiency?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.testTag("trip_efficiency_${trip.id}"))
            }
        }
    }
}
