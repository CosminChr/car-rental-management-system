package com.carrental.web.dto

import com.carrental.domain.Reservation
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateReservationRequest(
    val carId: UUID,
    val start: Instant,
    val end: Instant,
)

data class UpdateReservationRequest(
    val start: Instant,
    val end: Instant,
)

data class ReservationResponse(
    val id: UUID,
    val carId: UUID,
    val userId: String,
    val start: Instant,
    val end: Instant,
    val status: String,
    val price: BigDecimal?,
    val rentedAt: Instant?,
    val returnedAt: Instant?,
)

fun Reservation.toResponse(): ReservationResponse =
    ReservationResponse(
        id = id,
        carId = carId,
        userId = userId,
        start = startTs,
        end = endTs,
        status = status.name,
        price = price,
        rentedAt = rentedAt,
        returnedAt = returnedAt,
    )
