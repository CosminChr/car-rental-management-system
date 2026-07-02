package com.carrental.domain

import java.time.Instant

enum class CarState {
    AVAILABLE,
    RESERVED,
    RENTED;

    companion object {
        fun at(now: Instant, reservations: List<Reservation>): CarState = when {
            reservations.any { it.status == ReservationStatus.RENTED && it.covers(now) } -> RENTED
            reservations.any { it.status == ReservationStatus.RESERVED && it.covers(now) } -> RESERVED
            else -> AVAILABLE
        }
    }
}
