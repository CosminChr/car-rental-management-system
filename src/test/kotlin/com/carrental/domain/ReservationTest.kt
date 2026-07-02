package com.carrental.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ReservationTest {

    private val start: Instant = Instant.parse("2030-01-01T10:00:00Z")
    private val end: Instant = Instant.parse("2030-01-01T12:00:00Z")
    private val price = BigDecimal("20.00")

    private fun reserved() = Reservation(carId = UUID.randomUUID(), userId = "alice", startTs = start, endTs = end)

    @Test
    fun `cannot create a booking whose end is not after its start`() {
        assertThatThrownBy { Reservation(carId = UUID.randomUUID(), userId = "alice", startTs = end, endTs = start) }
            .isInstanceOf(DomainError.InvalidRentalPeriod::class.java)
    }

    @Test
    fun `a new booking is RESERVED with no price`() {
        val r = reserved()
        assertThat(r.status).isEqualTo(ReservationStatus.RESERVED)
        assertThat(r.price).isNull()
    }

    @Test
    fun `rent moves RESERVED to RENTED and records price and time`() {
        val r = reserved()
        r.rent(price, start)
        assertThat(r.status).isEqualTo(ReservationStatus.RENTED)
        assertThat(r.price).isEqualByComparingTo(price)
        assertThat(r.rentedAt).isEqualTo(start)
    }

    @Test
    fun `cannot rent a booking that is not RESERVED`() {
        val r = reserved()
        r.rent(price, start)
        assertThatThrownBy { r.rent(price, start) }
            .isInstanceOf(DomainError.ReservationNotReserved::class.java)
    }

    @Test
    fun `cannot rent outside the rental window`() {
        val r = reserved()
        assertThatThrownBy { r.rent(price, end.plusSeconds(3600)) }
            .isInstanceOf(DomainError.ReservationNotActive::class.java)
    }

    @Test
    fun `cannot rent at exactly the window end because the window is half-open`() {
        assertThatThrownBy { reserved().rent(price, end) }
            .isInstanceOf(DomainError.ReservationNotActive::class.java)
    }

    @Test
    fun `return moves RENTED to RETURNED`() {
        val r = reserved()
        r.rent(price, start)
        r.returnCar(end, BigDecimal.ZERO)
        assertThat(r.status).isEqualTo(ReservationStatus.RETURNED)
        assertThat(r.returnedAt).isEqualTo(end)
    }

    @Test
    fun `returning late adds the late fee to the price`() {
        val r = reserved()
        r.rent(price, start)
        r.returnCar(end.plusSeconds(3600), BigDecimal("10.00"))
        assertThat(r.price).isEqualByComparingTo(BigDecimal("30.00"))
    }

    @Test
    fun `cannot return a booking that is not RENTED`() {
        assertThatThrownBy { reserved().returnCar(start, BigDecimal.ZERO) }
            .isInstanceOf(DomainError.ReservationNotRented::class.java)
    }

    @Test
    fun `cancel moves RESERVED to CANCELLED`() {
        val r = reserved()
        r.cancel()
        assertThat(r.status).isEqualTo(ReservationStatus.CANCELLED)
    }

    @Test
    fun `cannot cancel a booking that is not RESERVED`() {
        val r = reserved()
        r.rent(price, start)
        assertThatThrownBy { r.cancel() }
            .isInstanceOf(DomainError.ReservationNotCancellable::class.java)
    }

    @Test
    fun `requireOwnedBy rejects a different user`() {
        assertThatThrownBy { reserved().requireOwnedBy("bob") }
            .isInstanceOf(DomainError.NotReservationOwner::class.java)
    }

    @Test
    fun `reschedule moves the window of a RESERVED booking`() {
        val r = reserved()
        r.reschedule(start.plusSeconds(3600), end.plusSeconds(3600))
        assertThat(r.startTs).isEqualTo(start.plusSeconds(3600))
        assertThat(r.endTs).isEqualTo(end.plusSeconds(3600))
    }

    @Test
    fun `cannot reschedule a booking that is not RESERVED`() {
        val r = reserved()
        r.rent(price, start)
        assertThatThrownBy { r.reschedule(start, end) }
            .isInstanceOf(DomainError.ReservationNotReschedulable::class.java)
    }

    @Test
    fun `cannot reschedule to a window whose end is not after its start`() {
        assertThatThrownBy { reserved().reschedule(end, start) }
            .isInstanceOf(DomainError.InvalidRentalPeriod::class.java)
        assertThatThrownBy { reserved().reschedule(start, start) }
            .isInstanceOf(DomainError.InvalidRentalPeriod::class.java)
    }
}
