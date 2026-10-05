package com.drivelink.demo.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Units
import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.core.domain.config.AppPreferences
import com.drivelink.core.domain.config.NotificationKind
import com.drivelink.core.domain.config.PinStore
import com.drivelink.core.domain.config.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The steps of Change PIN. */
enum class PinStep { CURRENT, NEW, CONFIRM }

/** The Change PIN dialog. [entered] holds the digits typed in the current step (0 to 4). */
data class ChangePinState(
    val step: PinStep = PinStep.CURRENT,
    val entered: String = "",
    val error: String? = null,
)

data class SettingsUiState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** The units of the account as text; read-only (D-13). */
    val units: String = Units.DEFAULT.describe(),
    /** The account units are not loaded; [units] shows the defaults. */
    val unitsAreDefault: Boolean = true,
    val notifications: Set<NotificationKind> = NotificationKind.entries.toSet(),
    val version: String = "",
    /** Not null while the Change PIN dialog is open. */
    val changePin: ChangePinState? = null,
    /** The last Change PIN finished. Cleared when a new one starts. */
    val pinChanged: Boolean = false,
)

/**
 * Settings sub-screen: theme (stored on the device, applies at once), the account units
 * (read-only), Change PIN (current PIN, new PIN, confirm; stored through [PinStore]) and local
 * notification switches.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val pins: PinStore,
    garage: GarageRepository,
    buildInfo: AppBuildInfo,
) : ViewModel() {

    private data class Pin(val flow: ChangePinState? = null, val changed: Boolean = false)

    private val pin = MutableStateFlow(Pin())

    /** The PIN typed in the "new" step, kept until the confirm step ends. */
    private var newPin: String = ""

    val state: StateFlow<SettingsUiState> = combine(
        preferences.themeMode, preferences.enabledNotifications, garage.state, pin,
    ) { theme, notifications, g, p ->
        val units = g.user?.units
        SettingsUiState(
            theme = theme,
            units = (units ?: Units.DEFAULT).describe(),
            unitsAreDefault = units == null,
            notifications = notifications,
            version = buildInfo.versionText(),
            changePin = p.flow,
            pinChanged = p.changed,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(version = buildInfo.versionText()))

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setNotification(kind: NotificationKind, enabled: Boolean) {
        viewModelScope.launch { preferences.setNotification(kind, enabled) }
    }

    /** Opens Change PIN. Without a stored PIN, the flow skips the "current" step. */
    fun startChangePin() {
        newPin = ""
        val first = if (pins.hasPin.value) PinStep.CURRENT else PinStep.NEW
        pin.value = Pin(ChangePinState(first))
    }

    fun cancelChangePin() {
        newPin = ""
        pin.update { it.copy(flow = null) }
    }

    fun pinDigit(digit: Int) {
        val current = pin.value.flow ?: return
        if (current.entered.length >= PIN_LENGTH) return
        val entered = current.entered + digit
        if (entered.length < PIN_LENGTH) {
            pin.value = pin.value.copy(flow = current.copy(entered = entered, error = null))
        } else {
            complete(current.step, entered)
        }
    }

    fun pinDelete() {
        val current = pin.value.flow ?: return
        pin.value = pin.value.copy(flow = current.copy(entered = current.entered.dropLast(1), error = null))
    }

    private fun complete(step: PinStep, entered: String) {
        when (step) {
            PinStep.CURRENT ->
                if (pins.verify(entered)) goTo(PinStep.NEW)
                else show(step, "The PIN is incorrect.")
            PinStep.NEW ->
                if (pins.hasPin.value && pins.verify(entered)) show(step, "Choose a PIN that is different from the current PIN.")
                else {
                    newPin = entered
                    goTo(PinStep.CONFIRM)
                }
            PinStep.CONFIRM ->
                if (entered == newPin) save(entered)
                else {
                    newPin = ""
                    show(PinStep.NEW, "The PINs do not match. Start again.")
                }
        }
    }

    private fun goTo(step: PinStep) {
        pin.value = pin.value.copy(flow = ChangePinState(step))
    }

    private fun show(step: PinStep, error: String) {
        pin.value = pin.value.copy(flow = ChangePinState(step, error = error))
    }

    private fun save(value: String) {
        viewModelScope.launch {
            pins.set(value)
            newPin = ""
            pin.value = Pin(flow = null, changed = true)
        }
    }

    private companion object {
        const val PIN_LENGTH = 4
    }
}
