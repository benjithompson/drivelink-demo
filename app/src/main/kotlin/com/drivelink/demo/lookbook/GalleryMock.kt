package com.drivelink.demo.lookbook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.drivelink.core.designsystem.art.BodyStyle
import com.drivelink.core.designsystem.art.VehiclePaint
import com.drivelink.core.designsystem.art.VehicleSideView
import com.drivelink.core.designsystem.component.ActionTile
import com.drivelink.core.designsystem.component.ActionTileRow
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.CommandProgressCard
import com.drivelink.core.designsystem.component.CommandStage
import com.drivelink.core.designsystem.component.DlAccentButton
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.DlOutlinedButton
import com.drivelink.core.designsystem.component.DlPrimaryButton
import com.drivelink.core.designsystem.component.ScenarioChip
import com.drivelink.core.designsystem.component.SectionTitle
import com.drivelink.core.designsystem.component.StatusChip
import com.drivelink.core.designsystem.component.StatusPill
import com.drivelink.core.designsystem.theme.DlTheme

private data class Swatch(val name: String, val color: Color)

/** Tokens and components in one scrolling page, for the look-and-feel review. */
@Composable
fun GalleryMock(modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    val swatches = listOf(
        Swatch("brand", c.brand), Swatch("accent", c.accent), Swatch("tile", c.tile),
        Swatch("heroTop", c.heroTop), Swatch("accentContainer", c.accentContainer), Swatch("success", c.success),
        Swatch("chargeHeader", c.chargeHeader), Swatch("warning", c.warning), Swatch("error", c.error),
        Swatch("row", c.row), Swatch("card", c.card), Swatch("divider", c.divider),
    )
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("screen_gallery"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("Colors")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            swatches.forEach { s ->
                Column(Modifier.width(96.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(s.color)
                            .border(1.dp, c.divider, RoundedCornerShape(10.dp)),
                    )
                    Text(s.name, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
        }
        SectionTitle("Type")
        val t = MaterialTheme.typography
        Text("84 %", style = t.displayMedium, color = c.textPrimary)
        Text("Headline · 2026 Aurora EV", style = t.headlineSmall, color = c.textPrimary)
        Text("Title · Vehicle Status", style = t.titleMedium, color = c.textPrimary)
        Text("Body · Your vehicle is locked and parked.", style = t.bodyLarge, color = c.textPrimary)
        Text("Caption · Updated 2 min ago", style = t.bodySmall, color = c.textSecondary)
        SectionTitle("Vehicles")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VehicleSideView(Modifier.weight(1f), BodyStyle.Crossover, VehiclePaint.ArcticWhite)
            VehicleSideView(Modifier.weight(1f), BodyStyle.Sedan, VehiclePaint.GraphiteGray)
        }
        SectionTitle("Tiles")
        ActionTileRow { m ->
            ActionTile(Icons.Filled.Lock, "Active", {}, m, active = true)
            ActionTile(Icons.Filled.Thermostat, "Inactive", {}, m)
            ActionTile(Icons.Filled.Lock, "Busy", {}, m, busy = true)
            ActionTile(Icons.Filled.Lock, "Disabled", {}, m, enabled = false)
        }
        SectionTitle("Buttons and chips")
        DlPrimaryButton("Primary", {}, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DlOutlinedButton("Outlined", {}, Modifier.weight(1f))
            DlAccentButton("Accent", {}, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("Charge Schedule", value = "On", onClick = {})
            StatusPill("Door Closed")
            StatusPill("Door Open", alert = true)
        }
        ScenarioChip("slow-vehicle", "mock.blazemeter.net")
        SectionTitle("Command progress")
        CommandStage.entries.forEach { stage ->
            CommandProgressCard("Remote Start", stage, onRetry = {})
        }
        SectionTitle("Banners")
        DlBanner(BannerKind.Stale, "Vehicle is offline. Status may be out of date.")
        DlBanner(BannerKind.Warning, "Too many requests. Retry in 30 s.")
        DlBanner(BannerKind.Error, "Something went wrong. ID 7f3c-91a2", actionLabel = "Retry", onAction = {})
        DlBanner(BannerKind.Info, "Charge complete.")
        Spacer(Modifier.size(24.dp))
    }
}
