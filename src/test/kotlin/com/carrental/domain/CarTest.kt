package com.carrental.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.time.Instant

class CarTest {

    private fun car(price: String = "10.00") =
        Car(make = "Tesla", model = "Model 3", year = 2024, pricePerHour = BigDecimal(price))

    @ParameterizedTest(name = "{0}s billed as {1} hours")
    @CsvSource(
        "3600, 1",
        "3601, 2",
        "1, 1",
        "7200, 2",
        "7201, 3",
    )
    fun `priceFor bills every started hour`(seconds: Long, hours: Long) {
        assertThat(car("10.00").priceFor(BASE, BASE.plusSeconds(seconds)))
            .isEqualByComparingTo(BigDecimal(hours * 10))
    }

    @Test
    fun `priceFor bills a sub-second rental as one full hour`() {
        assertThat(car("10.00").priceFor(BASE, BASE.plusMillis(500)))
            .isEqualByComparingTo(BigDecimal("10.00"))
    }

    private companion object {
        val BASE: Instant = Instant.parse("2030-01-01T00:00:00Z")
    }
}
