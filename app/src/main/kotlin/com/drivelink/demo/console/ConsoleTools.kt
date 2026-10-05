package com.drivelink.demo.console

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.drivelink.core.designsystem.component.DlOutlinedButton
import com.drivelink.core.designsystem.component.DlPrimaryButton
import com.drivelink.core.designsystem.component.DlTextField
import com.drivelink.core.designsystem.component.DlToggleRow
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.config.BuiltInProfiles
import com.drivelink.core.domain.config.EndpointProfile
import kotlinx.coroutines.launch

/**
 * The setup tools of the console, behind one expandable header.
 *
 * Tags: `console.tools` (header), `console.profile.add`, `console.profile.edit`,
 * `console.export.copy`, `console.export.share`, `console.import`, `console.vin`,
 * `console.vin.set`, `console.vin.clear`, `console.reset_clear_pin`, `console.reset_session`.
 */
@Composable
internal fun ToolsSection(
    state: ConsoleUiState,
    open: Boolean,
    onToggle: () -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: () -> Unit,
    onExport: (destination: String) -> String,
    onImport: (String) -> Unit,
    onSetVin: (String) -> Unit,
    onResetSession: (clearPin: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    val context = LocalContext.current
    var importing by rememberSaveable { mutableStateOf(false) }
    var vin by rememberSaveable(state.vin) { mutableStateOf(state.vin.orEmpty()) }
    var clearPin by rememberSaveable { mutableStateOf(false) }
    val idle = state.busyAction == null

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(vertical = 8.dp)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .testTag("console.tools"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Setup tools",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "Collapse setup tools" else "Expand setup tools",
                tint = c.textSecondary,
            )
        }
        if (open) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DlOutlinedButton("Add profile", onAddProfile, Modifier.weight(1f).testTag("console.profile.add"))
                DlOutlinedButton("Edit profile", onEditProfile, Modifier.weight(1f).testTag("console.profile.edit"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DlOutlinedButton(
                    "Copy",
                    { copyToClipboard(context, onExport("the clipboard")) },
                    Modifier.weight(1f).testTag("console.export.copy"),
                )
                DlOutlinedButton(
                    "Share",
                    { share(context, onExport("the share sheet")) },
                    Modifier.weight(1f).testTag("console.export.share"),
                )
                DlOutlinedButton("Import", { importing = true }, Modifier.weight(1f).testTag("console.import"), enabled = idle)
            }
            Text(
                "Copy and Share export the profiles as JSON, without API key values. Import keeps the keys already stored.",
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
            )
            DlTextField(
                value = vin,
                onValueChange = { vin = it },
                label = "VIN override",
                modifier = Modifier.testTag("console.vin"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DlOutlinedButton("Set VIN", { onSetVin(vin) }, Modifier.weight(1f).testTag("console.vin.set"))
                DlOutlinedButton(
                    "Clear VIN",
                    {
                        vin = ""
                        onSetVin("")
                    },
                    Modifier.weight(1f).testTag("console.vin.clear"),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Checkbox) { clearPin = !clearPin }
                    .testTag("console.reset_clear_pin"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = clearPin,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = c.brand, uncheckedColor = c.textSecondary),
                    modifier = Modifier.padding(12.dp),
                )
                Text("Also clear the PIN", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            }
            DlPrimaryButton(
                text = if (state.busyAction == ConsoleViewModel.ACTION_RESET) "Resetting…" else "Reset session",
                onClick = { onResetSession(clearPin) },
                enabled = idle,
                modifier = Modifier.fillMaxWidth().testTag("console.reset_session"),
            )
        }
    }
    if (importing) {
        ImportDialog(
            onDismiss = { importing = false },
            onImport = {
                importing = false
                onImport(it)
            },
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("DriveLink profiles", text))
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "DriveLink endpoint profiles")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share profiles").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun pasteFromClipboard(context: Context): String {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return ""
    return if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(context).toString() else ""
}

/** Import from pasted text. Tags: `console.import.dialog`, `console.import.text`, `console.import.paste`, `console.import.confirm`, `console.import.cancel`. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("console.import.dialog"),
        title = { Text("Import profiles") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste the JSON that Export made.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp).testTag("console.import.text"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                TextButton(
                    onClick = { text = pasteFromClipboard(context) },
                    modifier = Modifier.testTag("console.import.paste"),
                ) { Text("Paste from clipboard") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("console.import.confirm"),
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("console.import.cancel")) { Text("Cancel") }
        },
    )
}

/**
 * Add / edit form for one profile. [existing] is null for a new profile.
 *
 * A built-in profile keeps its name, and the Local profile keeps its base URL (the field is
 * disabled). The cloud profile takes an override; "Restore default" empties the field, and an
 * empty field saves the build URL. A stored API key value never shows: leave the field empty to keep it.
 *
 * Tags: `console.profile.editor`, `console.profile.field.name`, `.baseUrl`, `.apiKeyHeader`,
 * `.apiKeyValue`, `console.profile.baseUrl.restore` (cloud), `.override.auth`, `.override.vehicle`, `.override.alerts`, `toggle_clear_stored_key`
 * (a stored key exists), `toggle_trust_user_certificates`, `console.profile.keyStored`,
 * `console.profile.error`, `console.profile.save`, `console.profile.delete`,
 * `console.profile.delete.confirm`, `console.profile.delete.yes`, `console.profile.delete.no`.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ProfileEditor(
    existing: EndpointProfile?,
    onSave: suspend (ProfileDraft) -> String?,
    onDelete: suspend (String) -> String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(existing?.id, stateSaver = ProfileDraft.Saver) {
        mutableStateOf(existing?.let(ProfileDraft::of) ?: ProfileDraft())
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val builtIn = existing?.builtIn == true
    val fixedUrl = existing?.id == BuiltInProfiles.LOCAL_ID
    val cloud = existing?.id == BuiltInProfiles.CLOUD_ID
    val hasKey = existing?.apiKeyValue != null

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("console.profile.editor"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DlTextField(
            draft.name, { draft = draft.copy(name = it) }, "Name",
            Modifier.testTag("console.profile.field.name"), enabled = !builtIn,
        )
        DlTextField(
            draft.baseUrl, { draft = draft.copy(baseUrl = it) }, "Base URL (without /v1)",
            Modifier.testTag("console.profile.field.baseUrl"), enabled = !fixedUrl,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        if (cloud) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Leave empty to use the URL of this build.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                )
                TextButton(
                    onClick = { draft = draft.copy(baseUrl = "") },
                    modifier = Modifier.testTag("console.profile.baseUrl.restore"),
                ) { Text("Restore default") }
            }
        }
        DlTextField(
            draft.apiKeyHeader, { draft = draft.copy(apiKeyHeader = it) }, "API key header (optional)",
            Modifier.testTag("console.profile.field.apiKeyHeader"),
        )
        DlTextField(
            draft.apiKeyValue, { draft = draft.copy(apiKeyValue = it) }, "API key value",
            Modifier.testTag("console.profile.field.apiKeyValue"), password = true,
            enabled = !draft.clearApiKey,
        )
        if (hasKey) {
            Text(
                "A key is stored (encrypted). Leave the field empty to keep it.",
                modifier = Modifier.testTag("console.profile.keyStored"),
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
            )
            DlToggleRow(
                "Clear stored key",
                checked = draft.clearApiKey,
                onCheckedChange = { draft = draft.copy(clearApiKey = it) },
            )
        }
        DlToggleRow(
            "Trust user certificates",
            checked = draft.trustUserCerts,
            onCheckedChange = { draft = draft.copy(trustUserCerts = it) },
        )
        Text("Per-group host overrides (optional)", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        DlTextField(
            draft.authUrl, { draft = draft.copy(authUrl = it) }, "Auth URL (/auth)",
            Modifier.testTag("console.profile.field.override.auth"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        DlTextField(
            draft.vehicleUrl, { draft = draft.copy(vehicleUrl = it) }, "Vehicle URL (/vehicles, /commands)",
            Modifier.testTag("console.profile.field.override.vehicle"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        DlTextField(
            draft.alertsUrl, { draft = draft.copy(alertsUrl = it) }, "Alerts URL (/alerts)",
            Modifier.testTag("console.profile.field.override.alerts"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        error?.let {
            Text(
                it,
                modifier = Modifier.testTag("console.profile.error"),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = c.error,
            )
        }
        DlPrimaryButton(
            "Save profile",
            onClick = {
                scope.launch {
                    val message = onSave(draft)
                    if (message == null) onClose() else error = message
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("console.profile.save"),
        )
        if (existing != null && !builtIn) {
            DlOutlinedButton(
                "Delete profile",
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().testTag("console.profile.delete"),
            )
        } else if (builtIn) {
            Text(
                "A built-in profile cannot be deleted.",
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
            )
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("console.profile.delete.confirm"),
            title = { Text("Delete \"${existing.name}\"?") },
            text = { Text("The profile and its stored API key are removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            val message = onDelete(existing.id)
                            if (message == null) onClose() else error = message
                        }
                    },
                    modifier = Modifier.testTag("console.profile.delete.yes"),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("console.profile.delete.no")) {
                    Text("Cancel")
                }
            },
        )
    }
}
