package com.carrental.repository

import com.carrental.domain.Reservation
import com.carrental.domain.ReservationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ReservationRepository : JpaRepository<Reservation, UUID> {

    fun findByUserIdOrderByStartTsDescIdAsc(userId: String, pageable: Pageable): Page<Reservation>

    @Query(
        """
        SELECT r FROM Reservation r WHERE r.carId IN :carIds
        AND r.status IN :statuses AND r.startTs <= :now AND r.endTs > :now
        """,
    )
    fun findActiveCoveringForCars(
        @Param("carIds") carIds: Collection<UUID>,
        @Param("statuses") statuses: Collection<ReservationStatus>,
        @Param("now") now: Instant,
    ): List<Reservation>
}
