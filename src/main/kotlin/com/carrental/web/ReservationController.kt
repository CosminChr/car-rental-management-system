package com.carrental.web

import com.carrental.service.ReservationService
import com.carrental.web.dto.CreateReservationRequest
import com.carrental.web.dto.PageResponse
import com.carrental.web.dto.ReservationResponse
import com.carrental.web.dto.UpdateReservationRequest
import com.carrental.web.dto.toPageResponse
import com.carrental.web.dto.toResponse
import jakarta.validation.constraints.NotBlank
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

private const val USER_ID_HEADER = "X-User-Id"

@RestController
@RequestMapping("/api/reservations")
@Validated
class ReservationController(
    private val reservations: ReservationService,
) {
    @PostMapping
    fun reserve(
        @RequestHeader(USER_ID_HEADER) @NotBlank userId: String,
        @RequestBody request: CreateReservationRequest,
    ): ResponseEntity<ReservationResponse> {
        val created = reservations.reserve(request.carId, userId, request.start, request.end).toResponse()
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id).toUri()
        return ResponseEntity.created(location).body(created)
    }

    @PostMapping("/{id}/rent")
    fun rent(@RequestHeader(USER_ID_HEADER) @NotBlank userId: String, @PathVariable id: UUID): ReservationResponse =
        reservations.rent(id, userId).toResponse()

    @PostMapping("/{id}/return")
    fun returnCar(@RequestHeader(USER_ID_HEADER) @NotBlank userId: String, @PathVariable id: UUID): ReservationResponse =
        reservations.returnCar(id, userId).toResponse()

    @PostMapping("/{id}/cancel")
    fun cancel(@RequestHeader(USER_ID_HEADER) @NotBlank userId: String, @PathVariable id: UUID): ReservationResponse =
        reservations.cancel(id, userId).toResponse()

    @PatchMapping("/{id}")
    fun reschedule(
        @RequestHeader(USER_ID_HEADER) @NotBlank userId: String,
        @PathVariable id: UUID,
        @RequestBody request: UpdateReservationRequest,
    ): ReservationResponse =
        reservations.reschedule(id, userId, request.start, request.end).toResponse()

    @GetMapping
    fun list(
        @RequestHeader(USER_ID_HEADER) @NotBlank userId: String,
        pageable: Pageable,
    ): PageResponse<ReservationResponse> =
        reservations.listByUser(userId, pageable).toPageResponse { it.toResponse() }

    @GetMapping("/{id}")
    fun get(
        @RequestHeader(USER_ID_HEADER) @NotBlank userId: String,
        @PathVariable id: UUID,
    ): ReservationResponse =
        reservations.getByIdForUser(id, userId).toResponse()
}
