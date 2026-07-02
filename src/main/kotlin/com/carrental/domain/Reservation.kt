package com.carrental.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "reservations")
class Reservation(
    @Column(name = "car_id")
    val carId: UUID,
    @Column(name = "user_id")
    val userId: String,
    startTs: Instant,
    endTs: Instant,
    @Id
    val id: UUID = UUID.randomUUID(),
) {
    @Column(name = "start_ts")
    var startTs: Instant = startTs
        protected set

    @Column(name = "end_ts")
    var endTs: Instant = endTs
        protected set

    @Enumerated(EnumType.STRING)
    var status: ReservationStatus = ReservationStatus.RESERVED
        protected set

    @Column(precision = 13, scale = 2)
    var price: BigDecimal? = null
        protected set

    @Column(name = "rented_at")
    var rentedAt: Instant? = null
        protected set

    @Column(name = "returned_at")
    var returnedAt: Instant? = null
        protected set

    @Version
    var version: Long? = null
        protected set

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    var createdAt: Instant? = null
        protected set

    init {
        requireValidWindow(startTs, endTs)
    }

    fun rent(total: BigDecimal, at: Instant) {
        if (status != ReservationStatus.RESERVED) throw DomainError.ReservationNotReserved(id, status)
        if (!covers(at)) throw DomainError.ReservationNotActive(id, startTs, endTs)
        status = ReservationStatus.RENTED
        price = total
        rentedAt = at
    }

    fun returnCar(at: Instant, lateFee: BigDecimal) {
        if (status != ReservationStatus.RENTED) throw DomainError.ReservationNotRented(id, status)
        val total = checkNotNull(price) { "A RENTED reservation always has a price" } + lateFee
        status = ReservationStatus.RETURNED
        returnedAt = at
        price = total
    }

    fun cancel() {
        if (status != ReservationStatus.RESERVED) throw DomainError.ReservationNotCancellable(id, status)
        status = ReservationStatus.CANCELLED
    }

    fun reschedule(newStart: Instant, newEnd: Instant) {
        if (status != ReservationStatus.RESERVED) throw DomainError.ReservationNotReschedulable(id, status)
        requireValidWindow(newStart, newEnd)
        startTs = newStart
        endTs = newEnd
    }

    private fun requireValidWindow(start: Instant, end: Instant) {
        if (!end.isAfter(start)) {
            throw DomainError.InvalidRentalPeriod("Rental period end ($end) must be after start ($start)")
        }
    }

    fun requireOwnedBy(requester: String) {
        if (userId != requester) throw DomainError.NotReservationOwner(id)
    }

    fun covers(instant: Instant): Boolean =
        !instant.isBefore(startTs) && instant.isBefore(endTs)

    override fun equals(other: Any?): Boolean = this === other || (other is Reservation && id == other.id)

    override fun hashCode(): Int = id.hashCode()
}
