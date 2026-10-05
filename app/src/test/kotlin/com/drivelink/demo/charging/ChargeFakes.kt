package com.drivelink.demo.charging

import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.command.RunCommandUseCase
import com.drivelink.core.domain.model.ChargeSettings
import com.drivelink.core.domain.repository.VehicleRepository
import com.drivelink.demo.Examples
import com.drivelink.demo.FakeCommandRepository
import com.drivelink.demo.FakeVehicleRepository
import com.drivelink.demo.GarageHarness
import com.drivelink.demo.remote.RemoteCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.milliseconds

/** Vehicle fake with charge settings. The other calls come from [FakeVehicleRepository]. */
class ChargeVehicleRepository(private val base: FakeVehicleRepository) : VehicleRepository by base {
    var settings: Outcome<ChargeSettings> = Outcome.Ok(Examples.decode("getChargeSettings/200.default.json", ChargeSettings.serializer()))
    var putResult: Outcome<ChargeSettings>? = null
    var gets = 0
    val puts = mutableListOf<ChargeSettings>()

    override suspend fun getChargeSettings(vin: String): Outcome<ChargeSettings> {
        gets++
        return settings
    }

    override suspend fun updateChargeSettings(vin: String, settings: ChargeSettings): Outcome<ChargeSettings> {
        puts += settings
        return putResult ?: Outcome.Ok(settings)
    }
}

/** The shared fakes with a charge-capable vehicle repository, a garage and the command holder. */
class ChargeHarness {
    val h = GarageHarness()
    val vehicles = ChargeVehicleRepository(h.vehicles)
    val garage = GarageRepository(vehicles, h.alerts, h.account, h.auth, h.config)
    val remote = RemoteCommands(
        RunCommandUseCase(FakeCommandRepository(), TimingLog { _, _, _ -> }),
        garage,
        CoroutineScope(Dispatchers.Unconfined),
        2500.milliseconds,
    )
}
