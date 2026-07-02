package com.carrental.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "rental")
data class RentalProperties(
    val minDuration: Duration,
    val maxDuration: Duration,
) {
    init {
        require(minDuration > Duration.ZERO) { "rental.min-duration must be positive" }
        require(maxDuration >= minDuration) { "rental.max-duration must be >= rental.min-duration" }
    }
}
