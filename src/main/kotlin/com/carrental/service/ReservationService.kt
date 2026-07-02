package com.carrental.service

import com.carrental.config.RentalProperties
import com.carrental.domain.DomainError
import com.carrental.domain.Reservation
import com.carrental.repository.CarRepository
import com.carrental.repository.ReservationRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class ReservationService(
    private val cars: CarRepository,
    private val reservations: ReservationRepository,
    private val clock: Clock,
    private val props: RentalProperties,
) {
    @Transactional
    fun reserve(carId: UUID, userId: String, start: Instant, end: Instant): Reservation {
        validate(start, end)
        if (!cars.existsById(carId)) throw DomainError.CarNotFound(carId)
        return save(Reservation(carId = carId, userId = userId, startTs = start, endTs = end))
    }

    @Transactional
    fun rent(reservationId: UUID, userId: String): Reservation {
        val reservation = ownedReservation(reservationId, userId)
        val car = cars.findByIdOrNull(reservation.carId) ?: throw DomainError.CarNotFound(reservation.carId)
        reservation.rent(car.priceFor(reservation.startTs, reservation.endTs), Instant.now(clock))
        return reservation
    }

    @Transactional
    fun returnCar(reservationId: UUID, userId: String): Reservation {
        val reservation = ownedReservation(reservationId, userId)
        val now = Instant.now(clock)
        reservation.returnCar(now, lateFee(reservation, now))
        return reservation
    }

    private fun lateFee(reservation: Reservation, returnedAt: Instant): BigDecimal {
        if (!returnedAt.isAfter(reservation.endTs)) return BigDecimal.ZERO
        val car = cars.findByIdOrNull(reservation.carId) ?: throw DomainError.CarNotFound(reservation.carId)
        return car.priceFor(reservation.endTs, returnedAt)
    }

    @Transactional
    fun cancel(reservationId: UUID, userId: String): Reservation {
        val reservation = ownedReservation(reservationId, userId)
        reservation.cancel()
        return reservation
    }

    @Transactional
    fun reschedule(reservationId: UUID, userId: String, start: Instant, end: Instant): Reservation {
        val reservation = ownedReservation(reservationId, userId)
        validate(start, end)
        reservation.reschedule(start, end)
        return save(reservation)
    }

    @Transactional(readOnly = true)
    fun getByIdForUser(id: UUID, userId: String): Reservation = ownedReservation(id, userId)

    @Transactional(readOnly = true)
    fun listByUser(userId: String, pageable: Pageable): Page<Reservation> =
        reservations.findByUserIdOrderByStartTsDescIdAsc(userId, pageable)

    private fun ownedReservation(id: UUID, userId: String): Reservation {
        val reservation = reservations.findByIdOrNull(id) ?: throw DomainError.ReservationNotFound(id)
        reservation.requireOwnedBy(userId)
        return reservation
    }

    private fun save(reservation: Reservation): Reservation =
        try {
            reservations.saveAndFlush(reservation)
        } catch (e: DataIntegrityViolationException) {
            if (isOverlapViolation(e)) {
                throw DomainError.OverlappingReservation(reservation.carId, reservation.startTs, reservation.endTs, e)
            }
            throw e
        }

    private fun isOverlapViolation(e: DataIntegrityViolationException): Boolean =
        generateSequence(e as Throwable?) { it.cause }
            .filterIsInstance<SQLException>()
            .any { it.sqlState == EXCLUSION_VIOLATION }

    private fun validate(start: Instant, end: Instant) {
        if (!end.isAfter(start)) {
            throw DomainError.InvalidRentalPeriod("Rental period end ($end) must be after start ($start)")
        }
        if (start.isBefore(Instant.now(clock))) {
            throw DomainError.InvalidRentalPeriod("Rental start $start must not be in the past")
        }
        if (end.isAfter(MAX_BOOKING_INSTANT)) {
            throw DomainError.InvalidRentalPeriod("Rental end $end is beyond the maximum supported date $MAX_BOOKING_INSTANT")
        }
        checkDuration(Duration.between(start, end))
    }

    private fun checkDuration(duration: Duration) {
        if (duration < props.minDuration) {
            throw DomainError.DurationOutOfBounds("Rental duration $duration is below the minimum ${props.minDuration}")
        }
        if (duration > props.maxDuration) {
            throw DomainError.DurationOutOfBounds("Rental duration $duration exceeds the maximum ${props.maxDuration}")
        }
    }

    private companion object {
        const val EXCLUSION_VIOLATION = "23P01"
        val MAX_BOOKING_INSTANT: Instant = Instant.parse("9999-12-31T23:59:59Z")
    }
}
