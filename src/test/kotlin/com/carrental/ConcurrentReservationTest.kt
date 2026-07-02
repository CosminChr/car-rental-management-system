package com.carrental

import com.carrental.domain.Car
import com.carrental.domain.DomainError
import com.carrental.domain.ReservationStatus
import com.carrental.repository.ReservationRepository
import com.carrental.service.CarService
import com.carrental.service.ReservationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

class ConcurrentReservationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var carService: CarService

    @Autowired
    lateinit var reservationService: ReservationService

    @Autowired
    lateinit var reservationRepository: ReservationRepository

    @Autowired
    lateinit var clock: Clock

    private fun tesla() =
        carService.create(Car(make = "Tesla", model = "Model 3", year = 2024, pricePerHour = BigDecimal("10.00"))).car

    @Test
    fun `concurrent overlapping reservations leave exactly one winner`() {
        val car = tesla()
        val now = Instant.now(clock)
        val start = now.plusSeconds(3600)
        val end = now.plusSeconds(3 * 3600)
        val attempts = 8
        val barrier = CyclicBarrier(attempts)
        val pool = Executors.newFixedThreadPool(attempts)
        try {
            val tasks = (1..attempts).map { i ->
                Callable { runCatching { barrier.await(); reservationService.reserve(car.id, "user-$i", start, end) } }
            }
            val results = pool.invokeAll(tasks).map { it.get() }
            val winners = results.count { it.isSuccess }
            val losers = results.mapNotNull { it.exceptionOrNull() }
            assertThat(winners).isEqualTo(1)
            assertThat(losers).hasSize(attempts - 1)
            assertThat(losers).allMatch {
                it is DomainError.OverlappingReservation || it is PessimisticLockingFailureException
            }
            val persisted = reservationRepository.findAll().filter { it.carId == car.id }
            assertThat(persisted).hasSize(1)
            assertThat(persisted.single().status).isEqualTo(ReservationStatus.RESERVED)
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `concurrent rents on one reservation leave exactly one winner`() {
        val car = tesla()
        val now = Instant.now(clock)
        val reservation = reservationService.reserve(car.id, "alice", now, now.plusSeconds(2 * 3600))

        val threads = 2
        val barrier = CyclicBarrier(threads)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val tasks = (1..threads).map {
                Callable { runCatching { barrier.await(); reservationService.rent(reservation.id, "alice") } }
            }
            val results = pool.invokeAll(tasks).map { it.get() }
            assertThat(results.count { it.isSuccess }).isEqualTo(1)
            val loser = results.first { it.isFailure }.exceptionOrNull()
            assertThat(loser).isInstanceOfAny(
                ObjectOptimisticLockingFailureException::class.java,
                DomainError.ReservationNotReserved::class.java,
            )
            val persisted = reservationService.getByIdForUser(reservation.id, "alice")
            assertThat(persisted.status).isEqualTo(ReservationStatus.RENTED)
        } finally {
            pool.shutdown()
        }
    }
}
