package com.drivelink.demo.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.InfoRow
import com.drivelink.core.designsystem.component.SectionTitle
import com.drivelink.core.designsystem.theme.DlTheme

/** Navigation targets of the Menu tab. */
data class MenuActions(
    val onProfile: () -> Unit,
    val onSettings: () -> Unit,
    val onConsole: () -> Unit,
    val onGallery: () -> Unit,
)

/** Menu tab. The app shell draws the title bar. */
@Composable
fun MenuRoute(actions: MenuActions, vm: MenuViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MenuScreen(state, actions, onSignOut = vm::signOut)
}

/** The message that the stub rows show. */
private const val STUB_MESSAGE = "Not available in the demo"

/**
 * Test tags: `screen_menu`, `menu_header`, `menu_user_name`, `menu_user_email`, `menu_profile`,
 * `menu_settings`, `menu_digital_key`, `menu_roadside`, `menu_subscription`,
 * `menu_demo_console`, `menu_gallery`, `menu_about`, `menu_sign_out`; dialogs `menu_stub_dialog`,
 * `menu_stub_ok`, `menu_about_dialog`, `menu_about_ok`, `sign_out_confirm`, `sign_out_confirm_yes`, `sign_out_cancel`.
 */
@Composable
fun MenuScreen(
    state: MenuUiState,
    actions: MenuActions,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stub by rememberSaveable { mutableStateOf<String?>(null) }
    var about by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("screen_menu"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Header(state, onClick = actions.onProfile)
        SectionTitle("Account")
        InfoRow(Icons.Filled.Person, "Profile", "", onClick = actions.onProfile, modifier = Modifier.testTag("menu_profile"))
        InfoRow(Icons.Filled.Settings, "Settings", "", onClick = actions.onSettings, modifier = Modifier.testTag("menu_settings"))
        SectionTitle("Vehicle")
        InfoRow(
            Icons.Filled.Key, "Digital Key", "Not set up",
            onClick = { stub = "Digital Key" }, modifier = Modifier.testTag("menu_digital_key"),
        )
        InfoRow(
            Icons.Filled.SupportAgent, "Roadside Assistance", "",
            onClick = { stub = "Roadside Assistance" }, modifier = Modifier.testTag("menu_roadside"),
        )
        InfoRow(
            Icons.Filled.Badge, "Subscription", "Active",
            onClick = { stub = "Subscription" }, modifier = Modifier.testTag("menu_subscription"),
        )
        SectionTitle("Demo")
        InfoRow(Icons.Filled.Build, "Demo Console", "", onClick = actions.onConsole, modifier = Modifier.testTag("menu_demo_console"))
        InfoRow(Icons.Filled.Palette, "Design Gallery", "", onClick = actions.onGallery, modifier = Modifier.testTag("menu_gallery"))
        InfoRow(
            Icons.Filled.Info, "About", "v${state.version}",
            onClick = { about = true }, modifier = Modifier.testTag("menu_about"),
        )
        SectionTitle("Session")
        InfoRow(
            Icons.AutoMirrored.Filled.Logout, "Sign out", "", showDivider = false,
            onClick = { confirmSignOut = true }, modifier = Modifier.testTag("menu_sign_out"),
        )
    }
    stub?.let { name ->
        MenuDialog(
            title = name,
            text = STUB_MESSAGE + ".",
            dialogTag = "menu_stub_dialog",
            okTag = "menu_stub_ok",
            onDismiss = { stub = null },
        )
    }
    if (about) {
        MenuDialog(
            title = "About DriveLink",
            text = "Version ${state.version}. A demo app for testing. It is not the app of a car maker.",
            dialogTag = "menu_about_dialog",
            okTag = "menu_about_ok",
            onDismiss = { about = false },
        )
    }
    if (confirmSignOut) {
        ConfirmSignOutDialog(
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

@Composable
private fun Header(state: MenuUiState, onClick: () -> Unit) {
    val c = DlTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(c.card)
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag("menu_header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(c.brand),
            contentAlignment = Alignment.Center,
        ) {
            Text(initialsOf(state.name, state.email), style = MaterialTheme.typography.titleMedium, color = c.onBrand)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.name.ifEmpty { "DriveLink member" },
                modifier = Modifier.testTag("menu_user_name"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = c.textPrimary,
            )
            Text(
                state.email,
                modifier = Modifier.testTag("menu_user_email"),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.textSecondary)
    }
}

/** Up to two capital letters for the avatar: "Alex Rivera" gives "AR". Falls back to the email, then "D". */
internal fun initialsOf(name: String, email: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val letters = when {
        words.size >= 2 -> "${words.first().first()}${words.last().first()}"
        words.size == 1 -> words.first().take(1)
        else -> email.trim().take(1)
    }
    return letters.uppercase().ifEmpty { "D" }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuDialog(title: String, text: String, dialogTag: String, okTag: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(dialogTag),
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag(okTag)) { Text("OK") } },
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ConfirmSignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("sign_out_confirm"),
        title = { Text("Sign out?") },
        text = { Text("You will need to sign in again. Your PIN stays on this device.") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("sign_out_confirm_yes")) { Text("Sign out") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("sign_out_cancel")) { Text("Cancel") }
        },
    )
}
