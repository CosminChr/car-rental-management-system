package com.carrental.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class CarStateTest {

    private val now: Instant = Instant.parse("2030-01-01T12:00:00Z")

    private fun reservation(status: ReservationStatus, startHour: Long, endHour: Long): Reservation {
        val start = BASE.plusSeconds(startHour * 3600)
        val end = BASE.plusSeconds(endHour * 3600)
        return Reservation(carId = UUID.randomUUID(), userId = "u", startTs = start, endTs = end).apply {
            when (status) {
                ReservationStatus.RESERVED -> Unit
                ReservationStatus.RENTED -> rent(BigDecimal("10.00"), start)
                ReservationStatus.RETURNED -> {
                    rent(BigDecimal("10.00"), start)
                    returnCar(end, BigDecimal.ZERO)
                }
                ReservationStatus.CANCELLED -> cancel()
            }
        }
    }

    @Test
    fun `no reservations means AVAILABLE`() {
        assertThat(CarState.at(now, emptyList())).isEqualTo(CarState.AVAILABLE)
    }

    @Test
    fun `a future reservation leaves the car AVAILABLE now`() {
        assertThat(CarState.at(now, listOf(reservation(ReservationStatus.RESERVED, 20, 22))))
            .isEqualTo(CarState.AVAILABLE)
    }

    @Test
    fun `a RESERVED window covering now reads RESERVED`() {
        assertThat(CarState.at(now, listOf(reservation(ReservationStatus.RESERVED, 11, 13))))
            .isEqualTo(CarState.RESERVED)
    }

    @Test
    fun `a RENTED window covering now reads RENTED`() {
        assertThat(CarState.at(now, listOf(reservation(ReservationStatus.RENTED, 11, 13))))
            .isEqualTo(CarState.RENTED)
    }

    @Test
    fun `a covering RENTED reservation wins over a covering RESERVED one`() {
        val both = listOf(
            reservation(ReservationStatus.RESERVED, 11, 13),
            reservation(ReservationStatus.RENTED, 11, 13),
        )
        assertThat(CarState.at(now, both)).isEqualTo(CarState.RENTED)
    }

    @Test
    fun `a terminal-status reservation covering now leaves the car AVAILABLE`() {
        val terminal = listOf(
            reservation(ReservationStatus.RETURNED, 11, 13),
            reservation(ReservationStatus.CANCELLED, 11, 13),
        )
        assertThat(CarState.at(now, terminal)).isEqualTo(CarState.AVAILABLE)
    }

    private companion object {
        val BASE: Instant = Instant.parse("2030-01-01T00:00:00Z")
    }
}
