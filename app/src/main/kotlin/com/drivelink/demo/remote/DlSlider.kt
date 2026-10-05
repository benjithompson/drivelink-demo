package com.drivelink.demo.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivelink.core.designsystem.theme.DlTheme

/**
 * Slider in the app style: brand-colored track with ticks and a round thumb.
 *
 * The default Material 3 thumb is a thin vertical bar (4 dp wide). It looks like a stray line at
 * the end of the track, for example at 10 min on Remote Start. A round thumb reads as a slider
 * thumb at every value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DlSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = DlTheme.colors
    val colors = SliderDefaults.colors(
        thumbColor = c.brand,
        activeTrackColor = c.brand,
        inactiveTrackColor = c.tile,
        activeTickColor = c.onBrand,
        inactiveTickColor = c.brand,
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        thumb = { Box(Modifier.size(22.dp).background(c.brand, CircleShape)) },
        track = { state -> SliderDefaults.Track(state, colors = colors, thumbTrackGapSize = 0.dp) },
    )
}
