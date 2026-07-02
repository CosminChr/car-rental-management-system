package com.carrental.service

import com.carrental.config.RentalProperties
import com.carrental.domain.Car
import com.carrental.domain.DomainError
import com.carrental.domain.Reservation
import com.carrental.domain.ReservationStatus
import com.carrental.repository.CarRepository
import com.carrental.repository.ReservationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class ReservationServiceTest {

    private val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val cars = mockk<CarRepository>()
    private val reservations = mockk<ReservationRepository>()
    private val props = RentalProperties(Duration.ofHours(1), Duration.ofDays(30))
    private val service = ReservationService(cars, reservations, clock, props)

    private val carId = UUID.randomUUID()
    private val user = "alice"

    private fun car() = Car(make = "Tesla", model = "Model 3", year = 2024, pricePerHour = BigDecimal("10.00"), id = carId)

    private fun reserved(start: Instant, end: Instant) =
        Reservation(carId = carId, userId = user, startTs = start, endTs = end)

    private fun rented(start: Instant, end: Instant, price: BigDecimal = BigDecimal("20.00")) =
        reserved(start, end).apply { rent(price, start) }

    private fun from(hour: Long) = now.plusSeconds(hour * 3600)

    @Test
    fun `reserve saves a RESERVED booking owned by the user`() {
        every { cars.existsById(carId) } returns true
        val saved = slot<Reservation>()
        every { reservations.saveAndFlush(capture(saved)) } answers { saved.captured }

        val result = service.reserve(carId, user, from(1), from(3))

        assertThat(result.status).isEqualTo(ReservationStatus.RESERVED)
        assertThat(saved.captured.userId).isEqualTo(user)
    }

    @Test
    fun `reserve rejects an unknown car`() {
        every { cars.existsById(carId) } returns false
        assertThatThrownBy { service.reserve(carId, user, from(1), from(3)) }
            .isInstanceOf(DomainError.CarNotFound::class.java)
    }

    @Test
    fun `reserve rejects a start in the past`() {
        assertThatThrownBy { service.reserve(carId, user, now.minusSeconds(3600), from(1)) }
            .isInstanceOf(DomainError.InvalidRentalPeriod::class.java)
    }

    @Test
    fun `reserve rejects an end that is not after start`() {
        assertThatThrownBy { service.reserve(carId, user, from(3), from(1)) }
            .isInstanceOf(DomainError.InvalidRentalPeriod::class.java)
    }

    @Test
    fun `reserve rejects a start beyond the maximum supported calendar date`() {
        val farFuture = Instant.parse("+1000000-01-01T00:00:00Z")
        assertThatThrownBy { service.reserve(carId, user, farFuture, farFuture.plusSeconds(3600)) }
            .isInstanceOf(DomainError.InvalidRentalPeriod::class.java)
    }

    @Test
    fun `reserve rejects a duration below the minimum`() {
        assertThatThrownBy { service.reserve(carId, user, from(1), now.plusSeconds(3600 + 600)) }
            .isInstanceOf(DomainError.DurationOutOfBounds::class.java)
    }

    @Test
    fun `reserve rejects a duration above the maximum`() {
        assertThatThrownBy { service.reserve(carId, user, from(1), now.plusSeconds(3600).plus(Duration.ofDays(31))) }
            .isInstanceOf(DomainError.DurationOutOfBounds::class.java)
    }

    @Test
    fun `reserve accepts the exact minimum and maximum durations`() {
        every { cars.existsById(carId) } returns true
        every { reservations.saveAndFlush(any()) } answers { firstArg() }

        assertThat(service.reserve(carId, user, now, now.plus(Duration.ofHours(1))).status)
            .isEqualTo(ReservationStatus.RESERVED)
        assertThat(service.reserve(carId, user, now, now.plus(Duration.ofDays(30))).status)
            .isEqualTo(ReservationStatus.RESERVED)
    }

    @Test
    fun `rent computes the price and moves to RENTED`() {
        val r = reserved(now, from(2))
        every { reservations.findById(r.id) } returns Optional.of(r)
        every { cars.findById(carId) } returns Optional.of(car())

        val result = service.rent(r.id, user)

        assertThat(result.status).isEqualTo(ReservationStatus.RENTED)
        assertThat(result.price).isEqualByComparingTo(BigDecimal("20.00"))
    }

    @Test
    fun `rent rejects a reservation whose window has not started yet`() {
        val r = reserved(from(1), from(3))
        every { reservations.findById(r.id) } returns Optional.of(r)
        every { cars.findById(carId) } returns Optional.of(car())
        assertThatThrownBy { service.rent(r.id, user) }
            .isInstanceOf(DomainError.ReservationNotActive::class.java)
    }

    @Test
    fun `rent rejects a reservation whose window has already ended`() {
        val r = reserved(now.minusSeconds(3 * 3600), now.minusSeconds(3600))
        every { reservations.findById(r.id) } returns Optional.of(r)
        every { cars.findById(carId) } returns Optional.of(car())
        assertThatThrownBy { service.rent(r.id, user) }
            .isInstanceOf(DomainError.ReservationNotActive::class.java)
    }

    @Test
    fun `rent rejects a non-owner`() {
        val r = reserved(from(1), from(3))
        every { reservations.findById(r.id) } returns Optional.of(r)
        assertThatThrownBy { service.rent(r.id, "bob") }
            .isInstanceOf(DomainError.NotReservationOwner::class.java)
    }

    @Test
    fun `rent rejects an unknown reservation`() {
        val id = UUID.randomUUID()
        every { reservations.findById(id) } returns Optional.empty()
        assertThatThrownBy { service.rent(id, user) }
            .isInstanceOf(DomainError.ReservationNotFound::class.java)
    }

    @Test
    fun `returnCar rejects a non-owner`() {
        val r = rented(now, from(2))
        every { reservations.findById(r.id) } returns Optional.of(r)
        assertThatThrownBy { service.returnCar(r.id, "bob") }
            .isInstanceOf(DomainError.NotReservationOwner::class.java)
    }

    @Test
    fun `returnCar on time leaves the price unchanged`() {
        val r = rented(now.minusSeconds(2 * 3600), now)
        every { reservations.findById(r.id) } returns Optional.of(r)

        val result = service.returnCar(r.id, user)

        assertThat(result.status).isEqualTo(ReservationStatus.RETURNED)
        assertThat(result.price).isEqualByComparingTo(BigDecimal("20.00"))
    }

    @Test
    fun `returnCar bills each started overtime hour at the car rate as a late fee`() {
        val endedNinetyMinAgo = now.minusSeconds(90 * 60)
        val r = rented(now.minusSeconds(3 * 3600), endedNinetyMinAgo)
        every { reservations.findById(r.id) } returns Optional.of(r)
        every { cars.findById(carId) } returns Optional.of(car())

        val result = service.returnCar(r.id, user)

        assertThat(result.price).isEqualByComparingTo(BigDecimal("40.00"))
    }

    @Test
    fun `cancel moves a RESERVED booking to CANCELLED`() {
        val r = reserved(from(1), from(3))
        every { reservations.findById(r.id) } returns Optional.of(r)

        val result = service.cancel(r.id, user)

        assertThat(result.status).isEqualTo(ReservationStatus.CANCELLED)
    }

    @Test
    fun `cancel rejects a non-owner`() {
        val r = reserved(from(1), from(3))
        every { reservations.findById(r.id) } returns Optional.of(r)
        assertThatThrownBy { service.cancel(r.id, "bob") }
            .isInstanceOf(DomainError.NotReservationOwner::class.java)
    }

    @Test
    fun `cancel rejects an unknown reservation`() {
        val id = UUID.randomUUID()
        every { reservations.findById(id) } returns Optional.empty()
        assertThatThrownBy { service.cancel(id, user) }
            .isInstanceOf(DomainError.ReservationNotFound::class.java)
    }
}
