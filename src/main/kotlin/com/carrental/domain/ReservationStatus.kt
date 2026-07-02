package com.carrental.domain

enum class ReservationStatus {
    RESERVED,
    RENTED,
    RETURNED,
    CANCELLED;

    val isActive: Boolean get() = this == RESERVED || this == RENTED

    companion object {
        val ACTIVE: List<ReservationStatus> = entries.filter { it.isActive }
    }
}
