package com.drivelink.demo.nav

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.drivelink.core.designsystem.component.DlBottomBar
import com.drivelink.core.designsystem.component.DlNavItem
import com.drivelink.core.designsystem.component.DlSubTopBar
import com.drivelink.core.designsystem.component.DlTitleTopBar
import com.drivelink.core.designsystem.component.ScenarioChip
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.demo.console.DemoConsoleRoute
import com.drivelink.demo.home.HomeActions
import com.drivelink.demo.home.HomeRoute
import com.drivelink.demo.launch.LaunchRequest
import com.drivelink.demo.login.LoginRoute
import com.drivelink.demo.login.PinSetupRoute
import com.drivelink.demo.alerts.AlertsRoute
import com.drivelink.demo.carcare.CarCareRoute
import com.drivelink.demo.carcare.ServiceRequestRoute
import com.drivelink.demo.charging.ChargeScheduleRoute
import com.drivelink.demo.charging.ChargingRoute
import com.drivelink.demo.lookbook.GalleryMock
import com.drivelink.demo.maps.MapsRoute
import com.drivelink.demo.menu.MenuActions
import com.drivelink.demo.menu.MenuRoute
import com.drivelink.demo.menu.ProfileRoute
import com.drivelink.demo.menu.SettingsRoute
import com.drivelink.demo.status.StatusRoute
import com.drivelink.demo.trips.TripsRoute
import com.drivelink.demo.remote.ClimateRoute
import com.drivelink.demo.remote.ControlsRoute
import com.drivelink.demo.remote.PinEntryRoute

private val Tabs = listOf(
    DlNavItem("home", "Home", Icons.Filled.DirectionsCar),
    DlNavItem("carcare", "Car Care", Icons.Outlined.CheckCircle),
    DlNavItem("maps", "Maps", Icons.Filled.Navigation),
    DlNavItem("menu", "Menu", Icons.Filled.Menu),
)

private fun NavDestination?.tabKey(): String? = when {
    this == null -> null
    hasRoute<Home>() -> "home"
    hasRoute<CarCare>() -> "carcare"
    hasRoute<Maps>() -> "maps"
    hasRoute<Menu>() -> "menu"
    else -> null
}

private fun NavDestination?.subTitle(): String? = when {
    this == null -> null
    hasRoute<Controls>() -> "Remote Controls"
    hasRoute<Climate>() -> "Remote Start"
    hasRoute<PinEntry>() -> "Enter PIN"
    hasRoute<Charging>() -> "Charging"
    hasRoute<Status>() -> "Vehicle Status"
    hasRoute<Gallery>() -> "Design Gallery"
    hasRoute<ChargeSchedule>() -> "Charging Schedule"
    hasRoute<Trips>() -> "Trips"
    hasRoute<ServiceRequest>() -> "Schedule Service"
    hasRoute<Alerts>() -> "Alerts"
    hasRoute<Profile>() -> "Profile"
    hasRoute<Settings>() -> "Settings"
    else -> null
}

private fun tabRoute(key: String): Any = when (key) {
    "carcare" -> CarCare
    "maps" -> Maps
    "menu" -> Menu
    else -> Home
}

/** Switches to a bottom tab. The Home screen stays at the bottom of the back stack. */
private fun NavHostController.openTab(route: Any) {
    navigate(route) {
        popUpTo<Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Opens a sub-screen above the Home tab. */
private fun NavHostController.openSub(route: Any) {
    navigate(route) { launchSingleTop = true }
}

private fun NavHostController.goHome() {
    if (!popBackStack<Home>(inclusive = false)) {
        navigate(Home) { popUpTo(graph.id) { inclusive = true } }
    }
}

/**
 * The app shell: auth gate, bottom tabs, sub-screens with back, and the scenario chip.
 * [request] carries the launch extras (see [LaunchRequest]); a new request navigates.
 */
@Composable
fun AppNav(request: LaunchRequest?, appViewModel: AppViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val signedIn by appViewModel.signedIn.collectAsStateWithLifecycle()
    val chip by appViewModel.chip.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val tab = destination.tabKey()
    val subTitle = destination.subTitle()
    val onLightScreen = destination?.let { it.hasRoute<Login>() || it.hasRoute<PinSetup>() || it.hasRoute<Maps>() } == true

    // Auth gate: when the session ends, return to Login.
    LaunchedEffect(signedIn) {
        val current = nav.currentDestination ?: return@LaunchedEffect
        if (!signedIn && !current.hasRoute<Login>()) {
            appViewModel.onSignedOut()
            nav.navigate(Login) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }

    // Launch extras: open the requested screen.
    LaunchedEffect(request?.id) {
        val r = request ?: return@LaunchedEffect
        val route = ScreenNames.route(r.screen)
        if (route == Login) {
            nav.navigate(Login) { popUpTo(nav.graph.id) { inclusive = true } }
            return@LaunchedEffect
        }
        if (route == null) return@LaunchedEffect
        if (!signedIn) {
            // The Demo console has its own sign-in, and the gallery needs no data. Other screens need a session.
            if (route == Console || route == Gallery) nav.openSub(route)
            return@LaunchedEffect
        }
        appViewModel.onLaunchRequest(r.vin, scenarioChanged = r.scenario != null)
        // Close the sub-screens that are open. Without this, a tab route restores the saved sub-screen.
        nav.popBackStack<Home>(inclusive = false)
        when (route) {
            Home, CarCare, Maps, Menu -> nav.openTab(route)
            else -> {
                nav.openTab(Home)
                if (route == PinEntry) appViewModel.prepareDirectPin()
                nav.openSub(route)
            }
        }
    }

    // The navy top bar needs light status-bar icons; light screens need dark icons.
    val view = LocalView.current
    val lightIcons = onLightScreen && !DlTheme.isDark
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightIcons
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            when {
                subTitle != null -> DlSubTopBar(subTitle, onBack = { nav.popBackStack() })
                tab == "carcare" -> DlTitleTopBar("Car Care")
                tab == "menu" -> DlTitleTopBar("Menu")
            }
            Box(Modifier.weight(1f)) {
                AppGraph(nav, appViewModel)
            }
            if (tab != null) {
                DlBottomBar(Tabs, tab, onSelect = { nav.openTab(tabRoute(it)) })
            }
        }
        chip?.let {
            ScenarioChip(
                it.scenario, it.host,
                Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, bottom = if (tab != null) 76.dp else 12.dp),
                onClick = { nav.openSub(Console) },
            )
        }
    }
}

@Composable
private fun AppGraph(nav: NavHostController, app: AppViewModel) {
    NavHost(nav, startDestination = app.startRoute) {
        composable<Login> {
            LoginRoute(onSignedIn = { needsPin ->
                if (needsPin) nav.navigate(PinSetup) { popUpTo<Login> { inclusive = true } }
                else nav.navigate(Home) { popUpTo<Login> { inclusive = true } }
            })
        }
        composable<PinSetup> {
            PinSetupRoute(onDone = { nav.navigate(Home) { popUpTo<PinSetup> { inclusive = true } } })
        }
        composable<Home> {
            HomeRoute(
                HomeActions(
                    onLock = { nav.openSub(PinEntry) },
                    onClimate = { nav.openSub(Climate) },
                    onCharge = { nav.openSub(Charging) },
                    onFuel = { nav.openSub(Status) },
                    onControls = { nav.openSub(Controls) },
                    onLocation = { nav.openTab(Maps) },
                    onStatus = { nav.openSub(Status) },
                    onAlerts = { nav.openSub(Alerts) },
                    onTrips = { nav.openSub(Trips) },
                ),
            )
        }
        composable<CarCare> { CarCareRoute(onScheduleService = { nav.openSub(ServiceRequest) }) }
        composable<Maps> { MapsRoute() }
        composable<Menu> {
            MenuRoute(
                MenuActions(
                    onProfile = { nav.openSub(Profile) },
                    onSettings = { nav.openSub(Settings) },
                    onConsole = { nav.openSub(Console) },
                    onGallery = { nav.openSub(Gallery) },
                ),
            )
        }
        composable<Controls> {
            ControlsRoute(onPin = { nav.openSub(PinEntry) }, onStart = { nav.openSub(Climate) })
        }
        composable<Climate> { ClimateRoute(onPin = { nav.openSub(PinEntry) }) }
        composable<PinEntry> { PinEntryRoute(onDone = { nav.goHome() }) }
        composable<Charging> {
            ChargingRoute(
                onPin = { nav.openSub(PinEntry) },
                onSchedule = { nav.openSub(ChargeSchedule) },
                onFindStation = { nav.openTab(Maps) },
            )
        }
        composable<ChargeSchedule> { ChargeScheduleRoute(onDone = { nav.popBackStack() }) }
        composable<Status> { StatusRoute() }
        composable<Trips> { TripsRoute() }
        composable<ServiceRequest> { ServiceRequestRoute(onDone = { nav.popBackStack() }) }
        composable<Alerts> { AlertsRoute() }
        composable<Profile> { ProfileRoute() }
        composable<Settings> { SettingsRoute() }
        composable<Gallery> { GalleryMock() }
        composable<Console> {
            // The console draws its own top bar (it has a detail view with its own title).
            DemoConsoleRoute(onBack = { nav.popBackStack() })
        }
    }
}
