package com.drivelink.core.domain.format

import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.EfficiencyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormattersTest {
    @Test fun wholeDistance_groupsAndConverts() {
        assertThat(Formatters.wholeDistance(18452, DistanceUnit.MI)).isEqualTo("18,452mi")
        assertThat(Formatters.wholeDistance(18452, DistanceUnit.KM)).isEqualTo("29,696km")
    }

    @Test fun tripDistance_hasOneDecimal() {
        assertThat(Formatters.tripDistance(12.4, DistanceUnit.MI)).isEqualTo("12.4 mi")
        assertThat(Formatters.tripDistance(12.4, DistanceUnit.KM)).isEqualTo("20.0 km")
    }

    @Test fun shortDate_formatsOrReturnsNull() {
        assertThat(Formatters.shortDate("2026-06-12")).isEqualTo("06/12/26")
        assertThat(Formatters.shortDate("soon")).isNull()
    }

    @Test fun duration_usesHoursFromSixtyMinutes() {
        assertThat(Formatters.duration(26)).isEqualTo("26 min")
        assertThat(Formatters.duration(65)).isEqualTo("1 hr 5 min")
        assertThat(Formatters.duration(120)).isEqualTo("2 hr")
    }

    @Test fun efficiency_followsPowertrainAndUnits() {
        assertThat(Formatters.efficiency(3.9, EfficiencyUnit.MI_PER_KWH, DistanceUnit.MI)).isEqualTo("3.9 mi/kWh")
        assertThat(Formatters.efficiency(3.9, EfficiencyUnit.MI_PER_KWH, DistanceUnit.KM)).isEqualTo("6.3 km/kWh")
        assertThat(Formatters.efficiency(34.2, EfficiencyUnit.MPG, DistanceUnit.MI)).isEqualTo("34.2 MPG")
        assertThat(Formatters.efficiency(34.2, EfficiencyUnit.MPG, DistanceUnit.KM)).isEqualTo("6.9 L/100 km")
        assertThat(Formatters.efficiency(null, EfficiencyUnit.MPG, DistanceUnit.MI)).isNull()
        assertThat(Formatters.efficiency(3.9, null, DistanceUnit.MI)).isNull()
    }

    @Test fun dialNumber_keepsDigitsAndLeadingPlus() {
        assertThat(Formatters.dialNumber("+1 555 0142")).isEqualTo("+15550142")
        assertThat(Formatters.dialNumber("(555) 010-0142")).isEqualTo("5550100142")
    }

    @Test fun displayMessageWithId_appendsTheCorrelationId() {
        assertThat(AppError.Server(500, "c1").displayMessageWithId()).isEqualTo("Something went wrong on our side. (ID c1)")
    }

    @Test fun displayMessage_addsRetryAfter() {
        assertThat(AppError.RateLimited(30, "c").displayMessage()).isEqualTo("Too many requests. Try again in 30 seconds.")
        assertThat(AppError.RateLimited(null, "c").displayMessage()).isEqualTo("Too many requests. Wait and try again.")
        assertThat(AppError.Server(500, "c").displayMessage()).isEqualTo("Something went wrong on our side.")
    }
}
