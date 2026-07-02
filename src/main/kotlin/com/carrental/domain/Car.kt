package com.carrental.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "cars")
class Car(
    val make: String,
    val model: String,
    @Column(name = "model_year")
    val year: Int,
    @Column(name = "price_per_hour", precision = 10, scale = 2)
    val pricePerHour: BigDecimal,
    @Id
    val id: UUID = UUID.randomUUID(),
) {
    @Version
    var version: Long? = null
        protected set

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    var createdAt: Instant? = null
        protected set

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
        protected set

    fun priceFor(start: Instant, end: Instant): BigDecimal {
        val duration = Duration.between(start, end)
        val seconds = duration.seconds + if (duration.nano > 0) 1 else 0
        val hours = Math.ceilDiv(seconds, SECONDS_PER_HOUR)
        return pricePerHour.multiply(BigDecimal.valueOf(hours)).setScale(2, RoundingMode.HALF_UP)
    }

    override fun equals(other: Any?): Boolean = this === other || (other is Car && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    private companion object {
        const val SECONDS_PER_HOUR = 3600L
    }
}
