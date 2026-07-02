package com.carrental.service

import com.carrental.domain.Car
import com.carrental.domain.CarState
import com.carrental.domain.DomainError
import com.carrental.domain.ReservationStatus
import com.carrental.repository.CarRepository
import com.carrental.repository.ReservationRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CarView(val car: Car, val state: CarState)

@Service
class CarService(
    private val cars: CarRepository,
    private val reservations: ReservationRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun findAll(pageable: Pageable): Page<CarView> {
        val now = Instant.now(clock)
        val page = cars.findAll(pageable)
        val ids = page.content.map { it.id }
        val activeByCar = if (ids.isEmpty()) {
            emptyMap()
        } else {
            reservations.findActiveCoveringForCars(ids, ReservationStatus.ACTIVE, now).groupBy { it.carId }
        }
        return page.map { CarView(it, CarState.at(now, activeByCar[it.id].orEmpty())) }
    }

    @Transactional(readOnly = true)
    fun findAvailable(pageable: Pageable): Page<CarView> =
        cars.findAvailableNow(ReservationStatus.ACTIVE, Instant.now(clock), pageable).map { CarView(it, CarState.AVAILABLE) }

    @Transactional(readOnly = true)
    fun getById(id: UUID): CarView {
        val now = Instant.now(clock)
        val car = cars.findByIdOrNull(id) ?: throw DomainError.CarNotFound(id)
        return CarView(car, CarState.at(now, reservations.findActiveCoveringForCars(listOf(id), ReservationStatus.ACTIVE, now)))
    }

    @Transactional
    fun create(car: Car): CarView = CarView(cars.save(car), CarState.AVAILABLE)
}
