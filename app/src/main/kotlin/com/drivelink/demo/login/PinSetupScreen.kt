package com.drivelink.demo.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.PinPad
import com.drivelink.core.designsystem.theme.DlTheme

/** First sign-in: create the 4-digit PIN. The pad uses the same keys as the command PIN screen (`pin_key_*`). */
@Composable
fun PinSetupRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PinSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(top = 56.dp)
            .testTag("screen_pin_setup"),
    ) {
        PinPad(
            entered = state.entered,
            onDigit = viewModel::onDigit,
            onDelete = viewModel::onDelete,
            title = state.title,
            error = state.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You enter this PIN before each remote command.",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).testTag("pin_setup_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = DlTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
