package com.carrental.web

import com.carrental.domain.Car
import com.carrental.service.CarService
import com.carrental.web.dto.CarResponse
import com.carrental.web.dto.CreateCarRequest
import com.carrental.web.dto.PageResponse
import com.carrental.web.dto.toPageResponse
import com.carrental.web.dto.toResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.SortDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

@RestController
@RequestMapping("/api/cars")
class CarController(
    private val cars: CarService,
) {
    @GetMapping
    fun list(@SortDefault(sort = ["make", "model", "id"]) pageable: Pageable): PageResponse<CarResponse> =
        cars.findAll(pageable).toPageResponse { it.toResponse() }

    @GetMapping("/available")
    fun available(@SortDefault(sort = ["make", "model", "id"]) pageable: Pageable): PageResponse<CarResponse> =
        cars.findAvailable(pageable).toPageResponse { it.toResponse() }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): CarResponse = cars.getById(id).toResponse()

    @PostMapping
    fun create(@Valid @RequestBody request: CreateCarRequest): ResponseEntity<CarResponse> {
        val created = cars.create(
            Car(make = request.make, model = request.model, year = request.year, pricePerHour = request.pricePerHour),
        ).toResponse()
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id).toUri()
        return ResponseEntity.created(location).body(created)
    }
}
