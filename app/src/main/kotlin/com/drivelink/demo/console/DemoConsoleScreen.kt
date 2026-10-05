package com.drivelink.demo.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.DlCard
import com.drivelink.core.designsystem.component.DlOutlinedButton
import com.drivelink.core.designsystem.component.DlPrimaryButton
import com.drivelink.core.designsystem.component.DlSubTopBar
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.network.inspector.InspectorEntry
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Demo console with the Network Inspector. Launch extra `screen=console`, or Menu > Demo Console.
 *
 * Test tags (resource ids through testTagsAsResourceId): `screen_console`, `console.profile`,
 * `console.profile.<id>`, `console.profile.menu.add`, `console.profile.menu.edit`, `console.scenario`, `console.scenario.<name>`, `console.target`,
 * `console.signIn`, `console.health`, `console.vehicles`, `console.status`, `console.lock`,
 * `console.commandProgress`, `console.result`, `console.result.message`,
 * `console.result.correlationId`, `inspector.list`, `inspector.row.<n>` (0 = newest),
 * `inspector.empty`, `inspector.clear`. The detail view: see [InspectorDetail]. The setup tools
 * (profile add / edit / delete, import / export, VIN override, Reset session) and the profile
 * editor: see [ToolsSection] and [ProfileEditor].
 */
@Composable
fun DemoConsoleRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConsoleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = selectedId?.let { id -> state.entries.firstOrNull { it.id == id } }
    // The profile editor: null = closed, NEW_PROFILE = a new profile, else the id of the profile to edit.
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    // Kept here, so the setup tools stay open after the editor or the request detail closes.
    var toolsOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selected != null) { selectedId = null }
    BackHandler(enabled = editor != null) { editor = null }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (editor != null) {
            val existing = state.profiles.firstOrNull { it.id == editor }
            DlSubTopBar(if (existing == null) "New Profile" else "Edit Profile", onBack = { editor = null })
            ProfileEditor(
                existing = existing,
                onSave = viewModel::saveProfile,
                onDelete = viewModel::deleteProfile,
                onClose = { editor = null },
                modifier = Modifier.weight(1f),
            )
        } else if (selected != null) {
            DlSubTopBar("Request Detail", onBack = { selectedId = null })
            InspectorDetail(selected, toCurl = viewModel::toCurl, modifier = Modifier.weight(1f))
        } else {
            DlSubTopBar("Demo Console", onBack = onBack)
            DemoConsole(
                state = state,
                onProfile = viewModel::selectProfile,
                onAddProfile = { editor = NEW_PROFILE },
                onEditProfile = { editor = state.activeProfile?.id },
                onScenario = viewModel::selectScenario,
                onSignIn = viewModel::signIn,
                onHealth = viewModel::testConnection,
                onVehicles = viewModel::loadVehicles,
                onStatus = viewModel::loadStatus,
                onLock = viewModel::lock,
                onClear = viewModel::clearInspector,
                onEntry = { selectedId = it.id },
                tools = ConsoleTools(
                    open = toolsOpen,
                    onToggle = { toolsOpen = !toolsOpen },
                    onAddProfile = { editor = NEW_PROFILE },
                    onEditProfile = { editor = state.activeProfile?.id },
                    onExport = viewModel::exportProfiles,
                    onImport = viewModel::importProfiles,
                    onSetVin = { viewModel.setVin(it) },
                    onResetSession = { viewModel.resetSession(it) },
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The id that stands for "a new profile" in the editor state. No real profile uses it. */
private const val NEW_PROFILE = "__new__"

/** The callbacks of the setup tools. */
private class ConsoleTools(
    val open: Boolean,
    val onToggle: () -> Unit,
    val onAddProfile: () -> Unit,
    val onEditProfile: () -> Unit,
    val onExport: (destination: String) -> String,
    val onImport: (String) -> Unit,
    val onSetVin: (String) -> Unit,
    val onResetSession: (clearPin: Boolean) -> Unit,
)

@Composable
private fun DemoConsole(
    state: ConsoleUiState,
    onProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: () -> Unit,
    onScenario: (String) -> Unit,
    onSignIn: () -> Unit,
    onHealth: () -> Unit,
    onVehicles: () -> Unit,
    onStatus: () -> Unit,
    onLock: () -> Unit,
    onClear: () -> Unit,
    onEntry: (InspectorEntry) -> Unit,
    tools: ConsoleTools,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    val idle = state.busyAction == null
    Column(modifier.fillMaxWidth().testTag("screen_console")) {
        Column(
            Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val active = state.activeProfile
                Dropdown(
                    label = "Profile",
                    value = active?.name ?: "…",
                    tag = "console.profile",
                    description = "Endpoint profile: ${active?.name ?: "none"}",
                    options = state.profiles.map { Option(it.id, it.name, hostLabel(it)) },
                    onSelect = onProfile,
                    actions = listOfNotNull(
                        MenuAction("console.profile.menu.add", "Add endpoint…", onAddProfile),
                        active?.let { MenuAction("console.profile.menu.edit", "Edit \"${it.name}\"…", onEditProfile) },
                    ),
                    modifier = Modifier.weight(1f),
                )
                Dropdown(
                    label = "Scenario",
                    value = state.scenario,
                    tag = "console.scenario",
                    description = "Scenario: ${state.scenario}",
                    options = state.scenarios.map { Option(it, it, null) },
                    onSelect = onScenario,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                targetLine(state),
                modifier = Modifier.testTag("console.target"),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            if (rememberLocalNetworkBlocked(state.activeProfile?.baseUrl?.toHttpUrlOrNull()?.host)) {
                Text(
                    "This profile is on the local network. Allow local network access, or calls time out.",
                    modifier = Modifier.testTag("console.localNetworkWarning"),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = c.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(ConsoleViewModel.ACTION_SIGN_IN, "console.signIn", state, onSignIn, Modifier.weight(1f))
                ActionButton(ConsoleViewModel.ACTION_HEALTH, "console.health", state, onHealth, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(ConsoleViewModel.ACTION_VEHICLES, "console.vehicles", state, onVehicles, Modifier.weight(1f))
                ActionButton(ConsoleViewModel.ACTION_STATUS, "console.status", state, onStatus, Modifier.weight(1f))
            }
            DlPrimaryButton(
                text = if (state.busyAction == ConsoleViewModel.ACTION_LOCK) "Locking…" else "Lock",
                onClick = onLock,
                enabled = idle,
                modifier = Modifier.fillMaxWidth().testTag("console.lock"),
            )
            state.commandProgress?.let {
                Text(
                    "Command: $it",
                    modifier = Modifier.testTag("console.commandProgress"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textPrimary,
                )
            }
            ResultCard(state)
            ToolsSection(
                state = state,
                open = tools.open,
                onToggle = tools.onToggle,
                onAddProfile = tools.onAddProfile,
                onEditProfile = tools.onEditProfile,
                onExport = tools.onExport,
                onImport = tools.onImport,
                onSetVin = tools.onSetVin,
                onResetSession = tools.onResetSession,
            )
        }
        HorizontalDivider(color = c.divider)
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Network Inspector · ${state.entries.size}",
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onClear,
                enabled = state.entries.isNotEmpty(),
                modifier = Modifier.testTag("inspector.clear").semantics { contentDescription = "Clear inspector" },
            ) { Text("Clear") }
        }
        if (state.entries.isEmpty()) {
            Text(
                "No calls yet. Tap Test connection.",
                modifier = Modifier.padding(16.dp).testTag("inspector.empty"),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
        }
        // New entries are added at the top. LazyColumn keeps the first visible key in place, so
        // scroll back to the top when the list was at the top before.
        val listState = rememberLazyListState()
        val newestId = state.entries.firstOrNull()?.id
        LaunchedEffect(newestId) {
            if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).testTag("inspector.list"),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(state.entries, key = { _, e -> e.id }) { index, entry ->
                InspectorRow(entry, index, onClick = { onEntry(entry) })
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, tag: String, state: ConsoleUiState, onClick: () -> Unit, modifier: Modifier) {
    val running = state.busyAction == text
    DlOutlinedButton(
        text = if (running) "$text…" else text,
        onClick = onClick,
        enabled = state.busyAction == null,
        modifier = modifier.testTag(tag),
    )
}

@Composable
private fun ResultCard(state: ConsoleUiState) {
    val c = DlTheme.colors
    val result = state.result
    DlCard(Modifier.testTag("console.result")) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (result == null) {
                Text("No result yet.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (result.success) c.successStrong else c.error))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        result.action,
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textPrimary,
                    )
                }
                Text(
                    result.message,
                    modifier = Modifier.testTag("console.result.message"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                )
                result.correlationId?.let {
                    Text("Correlation id", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    Text(
                        it,
                        modifier = Modifier.testTag("console.result.correlationId"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorRow(entry: InspectorEntry, index: Int, onClick: () -> Unit) {
    val c = DlTheme.colors
    val url = entry.url.toHttpUrlOrNull()
    val path = url?.let { it.encodedPath + (it.encodedQuery?.let { q -> "?$q" } ?: "") } ?: entry.url
    val host = url?.let { if (it.port == defaultPort(it.scheme)) it.host else "${it.host}:${it.port}" } ?: ""
    val statusText = entry.status?.toString() ?: "ERR"
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("inspector.row.$index")
            .semantics { contentDescription = "${entry.method} $path $statusText ${entry.durationMs} ms" },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.method,
                modifier = Modifier.width(48.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = c.textPrimary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text(
                    listOfNotNull(host, entry.scenario?.takeIf { it != "default" }, entry.attempt.takeIf { it > 1 }?.let { "attempt $it" })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(entry.status)
            Text(
                "${entry.durationMs} ms",
                modifier = Modifier.widthIn(min = 64.dp).padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                maxLines = 1,
            )
        }
        HorizontalDivider(Modifier.padding(start = 16.dp), color = c.divider)
    }
}

/** Status color by class: 2xx green, 4xx amber, 5xx and transport errors red. */
@Composable
fun StatusBadge(status: Int?, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    val (background, foreground) = when (status) {
        in 200..399 -> c.successStrong to Color.White
        in 400..499 -> c.warning to Color(0xFF111418)
        else -> c.error to Color.White
    }
    Text(
        status?.toString() ?: "ERR",
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = foreground,
    )
}

private data class Option(val id: String, val label: String, val detail: String?)

/** A menu item below the options, after a divider. It runs [onClick] and selects nothing. */
private class MenuAction(val tag: String, val label: String, val onClick: () -> Unit)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Dropdown(
    label: String,
    value: String,
    tag: String,
    description: String,
    options: List<Option>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    actions: List<MenuAction> = emptyList(),
) {
    val c = DlTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, c.divider, RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(tag)
                .semantics { contentDescription = description },
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = c.textSecondary)
            }
        }
        // The menu is a popup with its own semantics root, so it needs testTagsAsResourceId too.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyMedium)
                            option.detail?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.id)
                    },
                    modifier = Modifier.testTag("$tag.${option.id}"),
                )
            }
            if (actions.isNotEmpty()) HorizontalDivider(color = c.divider)
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label, style = MaterialTheme.typography.bodyMedium, color = c.brand) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                    modifier = Modifier.testTag(action.tag),
                )
            }
        }
    }
}

private fun hostLabel(profile: EndpointProfile): String =
    ConsoleViewModel.hostOf(profile.baseUrl) ?: "not set"

private fun targetLine(state: ConsoleUiState): String {
    val host = state.activeProfile?.let(::hostLabel) ?: "…"
    val vin = state.vin ?: "no vehicle"
    val user = state.signedInAs ?: "signed out"
    return "Host $host · $vin · $user"
}

private fun defaultPort(scheme: String): Int = if (scheme == "https") 443 else 80
