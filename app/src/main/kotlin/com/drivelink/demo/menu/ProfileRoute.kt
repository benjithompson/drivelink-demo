package com.drivelink.demo.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.DlCard
import com.drivelink.core.designsystem.component.ErrorState
import com.drivelink.core.designsystem.component.SectionTitle
import com.drivelink.core.designsystem.theme.DlTheme

/** Profile sub-screen (getMe). The app shell draws the top bar with the back arrow. */
@Composable
fun ProfileRoute(modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(state, onRetry = viewModel::refresh, modifier = modifier)
}

/**
 * The API has no phone number (`User` has userId, name, email and units), so the screen shows none.
 *
 * Test tags: `screen_profile`, `profile_loading`, `profile_avatar`, `profile_name`, `profile_email`,
 * `profile_user_id`, `profile_units`, `profile_vehicle`, `profile_vehicle_vin`, and the error
 * tags `error_state`, `error_message`, `error_correlation_id`, `error_retry`.
 */
@Composable
fun ProfileScreen(state: ProfileUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("screen_profile"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val user = state.user
        when {
            user != null -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(80.dp).clip(CircleShape).background(c.brand).testTag("profile_avatar"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(initialsOf(user.name, user.email), style = MaterialTheme.typography.headlineSmall, color = c.onBrand)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        user.name,
                        modifier = Modifier.testTag("profile_name"),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = c.textPrimary,
                    )
                    Text(
                        user.email,
                        modifier = Modifier.testTag("profile_email"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }
                DlCard {
                    Column {
                        Field("Account ID", user.userId, "profile_user_id")
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.divider)
                        Field("Units", user.units.describe(), "profile_units")
                    }
                }
            }
            state.error != null -> ErrorState(
                message = state.error.message,
                correlationId = state.error.correlationId,
                onRetry = onRetry,
                title = "Couldn't load your profile",
            )
            else -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.brand, modifier = Modifier.testTag("profile_loading"))
            }
        }
        state.vehicle?.let { vehicle ->
            SectionTitle("Selected vehicle")
            DlCard(Modifier.testTag("profile_vehicle")) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(vehicle.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    if (vehicle.trim.isNotEmpty()) {
                        Text(vehicle.trim, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                    Text(
                        "VIN ${vehicle.vin}",
                        modifier = Modifier.testTag("profile_vehicle_vin"),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, tag: String) {
    val c = DlTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            modifier = Modifier.testTag(tag),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = c.textPrimary,
        )
    }
}
