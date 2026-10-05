package com.drivelink.demo.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.PinPad

/** PIN prompt before a remote command. [onDone] runs when the command started. */
@Composable
fun PinEntryRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PinEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    Column(modifier.padding(top = 40.dp).testTag("screen_pin")) {
        PinPad(
            entered = state.entered,
            onDigit = viewModel::onDigit,
            onDelete = viewModel::onDelete,
            error = state.error,
        )
    }
}
