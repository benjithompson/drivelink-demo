package com.drivelink.demo.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.core.domain.config.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Menu header and the About row show. */
data class MenuUiState(
    /** The account name; empty until the user loads. */
    val name: String = "",
    /** The account email. Falls back to the email of the session while the user loads. */
    val email: String = "",
    /** For example "0.1.0 (debug)". */
    val version: String = "",
)

/** The app version for display, from the build values. Example: client version `android/0.1.0` gives `0.1.0` and `0.1.0 (debug)` in a debug build. */
fun AppBuildInfo.versionText(): String = clientVersion.removePrefix("android/") + if (debug) " (debug)" else ""

/** Menu tab: the account header, the About version and Sign out. */
@HiltViewModel
class MenuViewModel @Inject constructor(
    private val garage: GarageRepository,
    sessions: SessionStore,
    buildInfo: AppBuildInfo,
) : ViewModel() {

    val state: StateFlow<MenuUiState> = combine(garage.state, sessions.session) { g, session ->
        MenuUiState(
            name = g.user?.name.orEmpty(),
            email = g.user?.email ?: session?.email.orEmpty(),
            version = buildInfo.versionText(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MenuUiState(
            name = garage.state.value.user?.name.orEmpty(),
            email = garage.state.value.user?.email ?: sessions.session.value?.email.orEmpty(),
            version = buildInfo.versionText(),
        ),
    )

    /** Clears the session and the vehicle data. The auth gate then opens Login. */
    fun signOut() {
        viewModelScope.launch { garage.signOut() }
    }
}
