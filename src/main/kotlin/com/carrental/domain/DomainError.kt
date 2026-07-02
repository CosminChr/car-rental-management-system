package com.carrental.domain

import java.time.Instant
import java.util.UUID

sealed class DomainError(message: String, val code: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    class CarNotFound(id: UUID) :
        DomainError("Car $id does not exist", "car-not-found")

    class ReservationNotFound(id: UUID) :
        DomainError("Reservation $id does not exist", "reservation-not-found")

    class OverlappingReservation(id: UUID, start: Instant, end: Instant, cause: Throwable? = null) :
        DomainError("Car $id already has an active booking overlapping [$start, $end)", "overlapping-reservation", cause)

    class InvalidRentalPeriod(reason: String) :
        DomainError(reason, "invalid-rental-period")

    class DurationOutOfBounds(reason: String) :
        DomainError(reason, "duration-out-of-bounds")

    class ReservationNotReserved(id: UUID, status: ReservationStatus) :
        DomainError("Reservation $id cannot be rented: expected RESERVED but was $status", "reservation-not-reserved")

    class ReservationNotActive(id: UUID, start: Instant, end: Instant) :
        DomainError("Reservation $id cannot be rented now: outside its rental window [$start, $end)", "reservation-not-active")

    class ReservationNotRented(id: UUID, status: ReservationStatus) :
        DomainError("Reservation $id cannot be returned: expected RENTED but was $status", "reservation-not-rented")

    class ReservationNotCancellable(id: UUID, status: ReservationStatus) :
        DomainError("Reservation $id cannot be cancelled: expected RESERVED but was $status", "reservation-not-cancellable")

    class ReservationNotReschedulable(id: UUID, status: ReservationStatus) :
        DomainError("Reservation $id cannot be rescheduled: expected RESERVED but was $status", "reservation-not-reschedulable")

    class NotReservationOwner(id: UUID) :
        DomainError("Reservation $id does not exist", "reservation-not-found")
}
