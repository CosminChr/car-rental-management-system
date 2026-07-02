package com.carrental.config

import com.carrental.domain.Car
import com.carrental.repository.CarRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.math.BigDecimal

@Configuration
@Profile("local")
class DataSeeder {

    @Bean
    fun seedCars(cars: CarRepository): CommandLineRunner = CommandLineRunner {
        if (cars.count() == 0L) {
            cars.saveAll(
                listOf(
                    Car(make = "Dacia", model = "Sandero", year = 2021, pricePerHour = BigDecimal("5.50")),
                    Car(make = "Volkswagen", model = "Golf", year = 2022, pricePerHour = BigDecimal("8.00")),
                    Car(make = "Skoda", model = "Octavia", year = 2023, pricePerHour = BigDecimal("9.00")),
                    Car(make = "Ford", model = "Focus", year = 2021, pricePerHour = BigDecimal("7.50")),
                    Car(make = "Toyota", model = "Corolla", year = 2023, pricePerHour = BigDecimal("9.50")),
                    Car(make = "Tesla", model = "Model 3", year = 2024, pricePerHour = BigDecimal("12.50")),
                    Car(make = "BMW", model = "i4", year = 2023, pricePerHour = BigDecimal("15.00")),
                    Car(make = "Audi", model = "A4", year = 2022, pricePerHour = BigDecimal("14.00")),
                    Car(make = "Mercedes-Benz", model = "C-Class", year = 2024, pricePerHour = BigDecimal("18.00")),
                    Car(make = "Volvo", model = "XC60", year = 2023, pricePerHour = BigDecimal("17.50")),
                    Car(make = "Tesla", model = "Model Y", year = 2024, pricePerHour = BigDecimal("16.00")),
                    Car(make = "Porsche", model = "911 Carrera", year = 2023, pricePerHour = BigDecimal("42.00")),
                ),
            )
        }
    }
}
