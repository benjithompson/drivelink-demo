package com.drivelink.demo.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drivelink.core.designsystem.component.ActionTile
import com.drivelink.core.designsystem.component.ActionTileRow
import com.drivelink.core.designsystem.component.SectionTitle
import com.drivelink.core.domain.model.CommandType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Remote Controls: a command remembers itself here, then the PIN screen confirms it. */
@HiltViewModel
class ControlsViewModel @Inject constructor(
    private val remote: RemoteCommands,
) : ViewModel() {
    /** The type of the command that runs now, for the busy tile. */
    val running: StateFlow<CommandType?> = remote.current
        .map { it?.takeIf { c -> c.running }?.type }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun request(type: CommandType) = remote.request(type)
}

/**
 * Remote Controls screen. [onPin] opens the PIN prompt after a command was requested.
 * [onStart] opens Remote Start.
 */
@Composable
fun ControlsRoute(
    onPin: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ControlsViewModel = hiltViewModel(),
) {
    val running by viewModel.running.collectAsStateWithLifecycle()
    fun run(type: CommandType) {
        viewModel.request(type)
        onPin()
    }
    Column(
        modifier
            .padding(20.dp)
            .testTag("screen_controls"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionTitle("Doors")
        ActionTileRow { m ->
            ActionTile(Icons.Filled.Lock, "Lock", { run(CommandType.LOCK) }, m, busy = running == CommandType.LOCK, testTag = "cmd_lock")
            ActionTile(Icons.Filled.LockOpen, "Unlock", { run(CommandType.UNLOCK) }, m, busy = running == CommandType.UNLOCK, testTag = "cmd_unlock")
            ActionTile(Icons.Filled.Campaign, "Horn & Lights", { run(CommandType.HORN_LIGHTS) }, m, busy = running == CommandType.HORN_LIGHTS, testTag = "cmd_horn_lights")
            ActionTile(Icons.Filled.RadioButtonChecked, "Lights", { run(CommandType.LIGHTS) }, m, busy = running == CommandType.LIGHTS, testTag = "cmd_lights")
        }
        SectionTitle("Engine and climate")
        ActionTileRow { m ->
            ActionTile(Icons.Filled.PowerSettingsNew, "Start", onStart, m, testTag = "cmd_start")
            ActionTile(Icons.Filled.Stop, "Stop", { run(CommandType.STOP) }, m, busy = running == CommandType.STOP, testTag = "cmd_stop")
            Spacer(m)
            Spacer(m)
        }
    }
}
