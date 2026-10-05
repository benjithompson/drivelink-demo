package com.drivelink.demo.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.art.BodyStyle
import com.drivelink.core.designsystem.art.VehiclePaint
import com.drivelink.core.designsystem.art.VehicleSideView
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.DlPrimaryButton
import com.drivelink.core.designsystem.component.DlTextField
import com.drivelink.core.designsystem.component.DriveLinkMark
import com.drivelink.core.designsystem.component.HeroBackdrop
import com.drivelink.core.designsystem.theme.DlTheme

/** Email and password sign-in. [onSignedIn] gets true on the first sign-in (the PIN is not set yet). */
@Composable
fun LoginRoute(
    onSignedIn: (needsPin: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.SignedIn -> onSignedIn(event.needsPin)
            }
        }
    }
    LoginScreen(state, viewModel::onEmail, viewModel::onPassword, viewModel::signIn, modifier)
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .testTag("screen_login"),
    ) {
        HeroBackdrop(Modifier.fillMaxWidth()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp, vertical = 24.dp)) {
                DriveLinkMark(Modifier.background(c.topBar, MaterialTheme.shapes.large).padding(horizontal = 14.dp, vertical = 8.dp))
                Spacer(Modifier.height(20.dp))
                VehicleSideView(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    style = BodyStyle.Crossover,
                    paint = VehiclePaint.GlacierBlue,
                )
            }
        }
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text("Sign in", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            state.notice?.let {
                DlBanner(BannerKind.Info, it, Modifier.testTag("login_notice"))
            }
            DlTextField(
                state.email, onEmail, "Email",
                Modifier.testTag("login_email"),
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            DlTextField(
                state.password, onPassword, "Password",
                Modifier.testTag("login_password"),
                enabled = !state.loading,
                password = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            state.error?.let { error ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("login_error"),
                ) {
                    DlBanner(BannerKind.Error, error.message)
                    error.correlationId?.let {
                        Text(
                            "ID: $it",
                            Modifier.padding(top = 4.dp, start = 4.dp).testTag("login_correlation_id"),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DlPrimaryButton(
                    if (state.loading) "Signing in…" else "Sign in",
                    onSubmit,
                    Modifier.weight(1f).testTag("login_submit"),
                    enabled = !state.loading,
                )
                if (state.loading) {
                    CircularProgressIndicator(
                        color = c.brand,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.padding(start = 12.dp).height(24.dp).testTag("login_loading"),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
