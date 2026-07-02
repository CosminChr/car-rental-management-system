package com.carrental.web

import com.carrental.domain.DomainError
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.data.core.PropertyReferenceException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DomainError::class)
    fun handleDomain(e: DomainError): ProblemDetail {
        val status = when (e) {
            is DomainError.CarNotFound,
            is DomainError.ReservationNotFound,
            is DomainError.NotReservationOwner -> HttpStatus.NOT_FOUND
            is DomainError.OverlappingReservation,
            is DomainError.ReservationNotReserved,
            is DomainError.ReservationNotRented,
            is DomainError.ReservationNotCancellable,
            is DomainError.ReservationNotReschedulable -> HttpStatus.CONFLICT
            is DomainError.ReservationNotActive,
            is DomainError.InvalidRentalPeriod,
            is DomainError.DurationOutOfBounds -> HttpStatus.UNPROCESSABLE_ENTITY
        }
        return problem(status, e.message ?: status.reasonPhrase, e.code)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(e: ConstraintViolationException): ProblemDetail {
        val detail = e.constraintViolations
            .joinToString("; ") { "${it.propertyPath.lastOrNull()?.name ?: "request"}: ${it.message}" }
            .ifEmpty { "Invalid request" }
        return problem(HttpStatus.BAD_REQUEST, detail, "validation-error")
    }

    @ExceptionHandler(PropertyReferenceException::class)
    fun handleInvalidSort(e: PropertyReferenceException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Unknown sort property '${e.propertyName}'", "invalid-sort")

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val detail = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage ?: "invalid"}" }
            .ifEmpty { "Invalid request" }
        return ResponseEntity.badRequest()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem(HttpStatus.BAD_REQUEST, detail, "validation-error"))
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLock(e: ObjectOptimisticLockingFailureException): ProblemDetail {
        log.debug("Optimistic lock conflict", e)
        return problem(HttpStatus.CONFLICT, "The resource was modified concurrently. Please retry.", "concurrent-modification")
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(e: DataIntegrityViolationException): ProblemDetail {
        log.warn("Data integrity violation", e)
        return problem(HttpStatus.CONFLICT, "The request conflicts with a data constraint.", "data-conflict")
    }

    @ExceptionHandler(PessimisticLockingFailureException::class)
    fun handleLockContention(e: PessimisticLockingFailureException): ProblemDetail {
        log.debug("Lock contention", e)
        return problem(HttpStatus.CONFLICT, "The resource is busy. Please retry.", "lock-contention")
    }

    @ExceptionHandler(CannotCreateTransactionException::class)
    fun handleConnectionUnavailable(e: CannotCreateTransactionException): ProblemDetail {
        log.warn("Could not acquire a database connection", e)
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily busy. Please retry.", "service-unavailable")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ProblemDetail {
        log.error("Unhandled exception", e)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", "internal-error")
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val response = super.handleExceptionInternal(ex, body, headers, statusCode, request)
        val code = HttpStatus.resolve(statusCode.value())?.reasonPhrase?.lowercase()?.replace(' ', '-') ?: "error"
        (response?.body as? ProblemDetail)?.let { stampCode(it, code) }
        return response
    }

    private fun problem(status: HttpStatus, detail: String, code: String): ProblemDetail =
        stampCode(ProblemDetail.forStatusAndDetail(status, detail), code)

    private fun stampCode(problem: ProblemDetail, code: String): ProblemDetail = problem.apply {
        if (properties?.containsKey("code") != true) {
            type = URI.create("$ERROR_TYPE_BASE/$code")
            setProperty("code", code)
        }
    }

    private companion object {
        const val ERROR_TYPE_BASE = "https://carrental.dev/errors"
    }
}
