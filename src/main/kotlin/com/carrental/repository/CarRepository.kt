package com.carrental.repository

import com.carrental.domain.Car
import com.carrental.domain.ReservationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface CarRepository : JpaRepository<Car, UUID> {

    @Query(
        """
        SELECT c FROM Car c WHERE NOT EXISTS (
            SELECT r FROM Reservation r WHERE r.carId = c.id
            AND r.status IN :statuses AND r.startTs <= :now AND r.endTs > :now
        )
        """,
    )
    fun findAvailableNow(
        @Param("statuses") statuses: Collection<ReservationStatus>,
        @Param("now") now: Instant,
        pageable: Pageable,
    ): Page<Car>
}
