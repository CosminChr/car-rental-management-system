package com.carrental.web.dto

import com.carrental.service.CarView
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

private const val MIN_CAR_YEAR = 1900L
private const val MAX_CAR_YEAR = 2100L

data class CreateCarRequest(
    @field:NotBlank @field:Size(max = 100) val make: String,
    @field:NotBlank @field:Size(max = 100) val model: String,
    @field:Min(MIN_CAR_YEAR) @field:Max(MAX_CAR_YEAR) val year: Int,
    @field:DecimalMin("0.01") @field:DecimalMax("10000.00") @field:Digits(integer = 8, fraction = 2) val pricePerHour: BigDecimal,
)

data class CarResponse(
    val id: UUID,
    val make: String,
    val model: String,
    val year: Int,
    val pricePerHour: BigDecimal,
    val state: String,
)

fun CarView.toResponse(): CarResponse =
    CarResponse(
        id = car.id,
        make = car.make,
        model = car.model,
        year = car.year,
        pricePerHour = car.pricePerHour,
        state = state.name,
    )
