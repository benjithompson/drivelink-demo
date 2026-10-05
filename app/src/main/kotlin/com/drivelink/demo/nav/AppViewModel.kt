package com.drivelink.demo.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.PinStore
import com.drivelink.core.domain.config.SessionStore
import com.drivelink.core.domain.model.CommandType
import com.drivelink.demo.remote.RemoteCommands
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

/** What the scenario chip shows. */
data class ScenarioChipUi(val scenario: String, val host: String)

/** App-level state: the auth gate and the scenario chip. */
@HiltViewModel
class AppViewModel @Inject constructor(
    sessionStore: SessionStore,
    pinStore: PinStore,
    demoConfig: DemoConfig,
    private val remote: RemoteCommands,
    private val garage: GarageRepository,
) : ViewModel() {

    /** Signed in now? Read synchronously, so the first frame already knows. */
    val signedIn: StateFlow<Boolean> = sessionStore.session
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionStore.session.value != null)

    /** The first destination: Login without a session, PIN setup without a PIN, else Home. */
    val startRoute: Any = when {
        sessionStore.session.value == null -> Login
        !pinStore.hasPin.value -> PinSetup
        else -> Home
    }

    /** Null when the scenario is `default`. */
    val chip: StateFlow<ScenarioChipUi?> = combine(demoConfig.scenario, demoConfig.activeProfile) { scenario, profile ->
        if (scenario == "default") null
        else ScenarioChipUi(scenario, hostLabel(profile.baseUrl, profile.name))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * A launch request arrived (a new intent): reload when the scenario or the profile changed,
     * and switch to the requested vehicle ([vin]).
     */
    fun onLaunchRequest(vin: String?, scenarioChanged: Boolean) {
        // A command card from the old scenario would confuse the new one.
        if (scenarioChanged) remote.reset()
        viewModelScope.launch {
            garage.ensureLoaded()
            if (vin != null) garage.selectVehicle(vin)
        }
    }

    /** `screen=pin` has no command behind it, so it opens the prompt for Lock. */
    fun prepareDirectPin() = remote.request(CommandType.LOCK)

    /**
     * The session ended: drop the pending and running commands. When the auth interceptor ended it
     * (the token refresh failed), the screens are gone before they can report it, so set the login notice here.
     */
    fun onSignedOut() {
        remote.reset()
        garage.noteSessionEnded()
    }

    private fun hostLabel(baseUrl: String, fallback: String): String {
        val url = baseUrl.toHttpUrlOrNull() ?: return fallback
        val default = url.port == (if (url.isHttps) 443 else 80)
        return if (default) url.host else "${url.host}:${url.port}"
    }
}
