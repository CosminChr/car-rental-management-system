package com.carrental

import com.carrental.domain.Car
import com.carrental.domain.CarState
import com.carrental.domain.Reservation
import com.carrental.repository.ReservationRepository
import com.carrental.service.CarService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

class CarAvailabilityIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var carService: CarService

    @Autowired
    lateinit var reservations: ReservationRepository

    @Autowired
    lateinit var clock: Clock

    private fun car(make: String, price: String = "10.00") =
        carService.create(Car(make = make, model = "Car", year = 2024, pricePerHour = BigDecimal(price))).car

    private fun rented(carId: UUID, start: Instant, end: Instant) =
        Reservation(carId = carId, userId = "owner", startTs = start, endTs = end)
            .apply { rent(BigDecimal("20.00"), start) }

    @Test
    fun `a reservation covering now makes the car RENTED and excludes it from availability`() {
        val now = Instant.now(clock)
        val busy = car("Busy")
        val free = car("Free")

        reservations.save(rented(busy.id, now.minusSeconds(3600), now.plusSeconds(3600)))

        val availableIds = carService.findAvailable(Pageable.unpaged()).content.map { it.car.id }
        assertThat(availableIds).contains(free.id).doesNotContain(busy.id)

        assertThat(carService.getById(busy.id).state).isEqualTo(CarState.RENTED)
        assertThat(carService.getById(free.id).state).isEqualTo(CarState.AVAILABLE)

        val states = carService.findAll(Pageable.unpaged()).content.associate { it.car.id to it.state }
        assertThat(states[busy.id]).isEqualTo(CarState.RENTED)
        assertThat(states[free.id]).isEqualTo(CarState.AVAILABLE)
    }

    @Test
    fun `a reservation ending exactly at now leaves the car available (half-open)`() {
        val now = Instant.now(clock)
        val car = car("Boundary")

        reservations.save(rented(car.id, now.minusSeconds(3600), now))

        assertThat(carService.getById(car.id).state).isEqualTo(CarState.AVAILABLE)
        assertThat(carService.findAvailable(Pageable.unpaged()).content.map { it.car.id }).contains(car.id)
    }

    @Test
    fun `a RESERVED reservation covering now excludes the car from availability`() {
        val now = Instant.now(clock)
        val car = car("Held")

        reservations.save(
            Reservation(carId = car.id, userId = "owner", startTs = now.minusSeconds(3600), endTs = now.plusSeconds(3600)),
        )

        assertThat(carService.findAvailable(Pageable.unpaged()).content.map { it.car.id }).doesNotContain(car.id)
        assertThat(carService.getById(car.id).state).isEqualTo(CarState.RESERVED)
    }

    @Test
    fun `an overdue RENTED reservation past its window leaves the car available (window-based scope)`() {
        val now = Instant.now(clock)
        val car = car("Overdue")

        reservations.save(rented(car.id, now.minusSeconds(2 * 3600), now.minusSeconds(3600)))

        assertThat(carService.getById(car.id).state).isEqualTo(CarState.AVAILABLE)
        assertThat(carService.findAvailable(Pageable.unpaged()).content.map { it.car.id }).contains(car.id)
    }

    @Test
    fun `reservations sharing a start are ordered by id as a stable tiebreaker`() {
        val now = Instant.now(clock)
        val user = "tiebreak-user"
        val carA = car("A")
        val carB = car("B")
        val start = now.plusSeconds(3600)
        val end = now.plusSeconds(7200)
        val lowId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val highId = UUID.fromString("00000000-0000-0000-0000-000000000002")

        reservations.save(Reservation(carId = carB.id, userId = user, startTs = start, endTs = end, id = highId))
        reservations.save(Reservation(carId = carA.id, userId = user, startTs = start, endTs = end, id = lowId))

        assertThat(reservations.findByUserIdOrderByStartTsDescIdAsc(user, Pageable.unpaged()).content.map { it.id })
            .containsExactly(lowId, highId)
    }
}
