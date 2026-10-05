package com.drivelink.core.domain

import com.drivelink.core.domain.model.DistanceUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class UnitsTest {
    @Test fun miles_toKilometers() {
        assertThat(Units.distanceRounded(226, DistanceUnit.KM)).isEqualTo(364)
        assertThat(Units.distanceRounded(226, DistanceUnit.MI)).isEqualTo(226)
        assertThat(Units.distanceLabel(DistanceUnit.KM)).isEqualTo("km")
    }

    @Test fun fahrenheit_celsius_roundTripsInsideTheStepperRange() {
        assertThat(Units.fahrenheitToCelsius(72)).isEqualTo(22)
        assertThat(Units.fahrenheitToCelsius(62)).isEqualTo(17)
        assertThat(Units.fahrenheitToCelsius(82)).isEqualTo(28)
        assertThat(Units.celsiusToFahrenheit(22)).isEqualTo(72)
        assertThat(Units.celsiusToFahrenheit(17)).isEqualTo(63)
        assertThat(Units.celsiusToFahrenheit(28)).isEqualTo(82)
    }

    @Test fun celsiusToFahrenheit_staysInApiRange() {
        (Units.MIN_TEMP_C..Units.MAX_TEMP_C).forEach {
            assertThat(Units.celsiusToFahrenheit(it)).isIn(Units.MIN_TEMP_F..Units.MAX_TEMP_F)
        }
        assertThat(Units.celsiusToFahrenheit(40)).isEqualTo(82)
    }

    @Test fun timeAgo() {
        val now = Instant.parse("2026-10-03T12:00:00Z")
        assertThat(TimeAgo.describe("2026-10-03T11:59:40Z", now)).isEqualTo("just now")
        assertThat(TimeAgo.describe("2026-10-03T11:55:00Z", now)).isEqualTo("5 min ago")
        assertThat(TimeAgo.describe("2026-10-03T09:00:00Z", now)).isEqualTo("3 hr ago")
        assertThat(TimeAgo.describe("2026-10-02T09:00:00Z", now)).isEqualTo("1 day ago")
        assertThat(TimeAgo.describe("2026-09-30T12:00:00Z", now)).isEqualTo("3 days ago")
        assertThat(TimeAgo.describe("not a date", now)).isNull()
    }
}
