package com.carrental

import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig::class)
abstract class AbstractIntegrationTest {

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun resetDatabase() {
        jdbc.execute("TRUNCATE reservations, cars CASCADE")
    }

    companion object {
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18")).apply { start() }
    }
}
