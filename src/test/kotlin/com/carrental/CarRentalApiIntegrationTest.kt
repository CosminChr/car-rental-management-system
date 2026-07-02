package com.carrental

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
class CarRentalApiIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var clock: Clock

    private fun createCar(): String {
        val response = mockMvc.post("/api/cars") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"make":"Tesla","model":"Model 3","year":2024,"pricePerHour":10.00}"""
        }.andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        return JsonPath.read(response, "$.id")
    }

    private fun reservationBody(carId: String, fromHour: Long, toHour: Long): String {
        val now = Instant.now(clock)
        return """{"carId":"$carId","start":"${now.plusSeconds(fromHour * 3600)}","end":"${now.plusSeconds(toHour * 3600)}"}"""
    }

    private fun reserve(carId: String, user: String, fromHour: Long, toHour: Long) =
        mockMvc.post("/api/reservations") {
            header("X-User-Id", user)
            contentType = MediaType.APPLICATION_JSON
            content = reservationBody(carId, fromHour, toHour)
        }

    private fun reschedule(id: String, user: String, fromHour: Long, toHour: Long) =
        mockMvc.patch("/api/reservations/$id") {
            header("X-User-Id", user)
            contentType = MediaType.APPLICATION_JSON
            val now = Instant.now(clock)
            content = """{"start":"${now.plusSeconds(fromHour * 3600)}","end":"${now.plusSeconds(toHour * 3600)}"}"""
        }

    private fun reserveReturningId(carId: String, user: String, fromHour: Long, toHour: Long): String {
        val body = reserve(carId, user, fromHour, toHour)
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        return JsonPath.read(body, "$.id")
    }

    @Test
    fun `full lifecycle reserve rent return`() {
        val carId = createCar()

        val reserved = reserve(carId, "alice", 0, 2)
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        val reservationId: String = JsonPath.read(reserved, "$.id")

        mockMvc.post("/api/reservations/$reservationId/rent") {
            header("X-User-Id", "alice")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("RENTED") }
            jsonPath("$.price") { value(20.00) }
        }

        mockMvc.post("/api/reservations/$reservationId/return") {
            header("X-User-Id", "alice")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("RETURNED") }
        }
    }

    @Test
    fun `overlapping reservation returns 409 problem json`() {
        val carId = createCar()
        reserve(carId, "alice", 1, 3).andExpect { status { isCreated() } }

        reserve(carId, "bob", 2, 4).andExpect {
            status { isConflict() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(409) }
        }
    }

    @Test
    fun `back-to-back reservations on the same car are both accepted`() {
        val carId = createCar()
        reserve(carId, "alice", 1, 3).andExpect { status { isCreated() } }
        reserve(carId, "bob", 3, 5).andExpect { status { isCreated() } }
    }

    @Test
    fun `renting before the rental window starts returns 422`() {
        val carId = createCar()
        val reserved = reserve(carId, "alice", 2, 4)
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        val reservationId: String = JsonPath.read(reserved, "$.id")

        mockMvc.post("/api/reservations/$reservationId/rent") {
            header("X-User-Id", "alice")
        }.andExpect {
            status { isUnprocessableContent() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("reservation-not-active") }
        }
    }

    @Test
    fun `another user cannot read rent return or cancel the reservation`() {
        val carId = createCar()
        val reserved = reserve(carId, "alice", 1, 3)
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        val reservationId: String = JsonPath.read(reserved, "$.id")

        mockMvc.get("/api/reservations/$reservationId") {
            header("X-User-Id", "bob")
        }.andExpect { status { isNotFound() } }

        mockMvc.post("/api/reservations/$reservationId/rent") {
            header("X-User-Id", "bob")
        }.andExpect { status { isNotFound() } }

        mockMvc.post("/api/reservations/$reservationId/return") {
            header("X-User-Id", "bob")
        }.andExpect { status { isNotFound() } }

        mockMvc.post("/api/reservations/$reservationId/cancel") {
            header("X-User-Id", "bob")
        }.andExpect { status { isNotFound() } }

        mockMvc.get("/api/reservations/$reservationId") {
            header("X-User-Id", "alice")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("RESERVED") }
        }
    }

    @Test
    fun `end before start returns 422`() {
        val carId = createCar()
        reserve(carId, "alice", 3, 1).andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun `blank user header returns 400`() {
        val carId = createCar()
        mockMvc.post("/api/reservations") {
            header("X-User-Id", "")
            contentType = MediaType.APPLICATION_JSON
            content = reservationBody(carId, 1, 3)
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `missing user header returns 400`() {
        val carId = createCar()
        mockMvc.post("/api/reservations") {
            contentType = MediaType.APPLICATION_JSON
            content = reservationBody(carId, 1, 3)
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `reserving an unknown car returns 404`() {
        reserve(UUID.randomUUID().toString(), "alice", 1, 3).andExpect { status { isNotFound() } }
    }

    @Test
    fun `cancelling a reservation frees the slot for re-booking`() {
        val carId = createCar()
        val reserved = reserve(carId, "alice", 1, 3)
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        val reservationId: String = JsonPath.read(reserved, "$.id")

        reserve(carId, "bob", 2, 4).andExpect { status { isConflict() } }

        mockMvc.post("/api/reservations/$reservationId/cancel") {
            header("X-User-Id", "alice")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CANCELLED") }
        }

        reserve(carId, "bob", 2, 4).andExpect { status { isCreated() } }
    }

    @Test
    fun `read endpoints expose cars and the caller's reservations`() {
        val carId = createCar()

        mockMvc.get("/api/cars/$carId").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(carId) }
            jsonPath("$.state") { value("AVAILABLE") }
        }

        val carsJson = mockMvc.get("/api/cars")
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(carsJson, "$.content[*].id")).contains(carId)

        val availableJson = mockMvc.get("/api/cars/available")
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(availableJson, "$.content[*].id")).contains(carId)

        val owner = "list-owner"
        val other = "list-other"
        val reserved = reserve(carId, owner, 1, 3)
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        val reservationId: String = JsonPath.read(reserved, "$.id")

        val mine = mockMvc.get("/api/reservations") { header("X-User-Id", owner) }
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(mine, "$.content[*].id")).contains(reservationId)

        val theirs = mockMvc.get("/api/reservations") { header("X-User-Id", other) }
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(theirs, "$.content[*].id")).doesNotContain(reservationId)
    }

    @Test
    fun `car listing is paginated`() {
        repeat(3) { createCar() }

        mockMvc.get("/api/cars?page=0&size=2").andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(2) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(2) }
            jsonPath("$.totalElements") { value(3) }
            jsonPath("$.totalPages") { value(2) }
        }
    }

    @Test
    fun `available car listing is paginated`() {
        repeat(3) { createCar() }

        mockMvc.get("/api/cars/available?page=0&size=2").andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(2) }
            jsonPath("$.totalElements") { value(3) }
            jsonPath("$.totalPages") { value(2) }
        }
    }

    @Test
    fun `reservations are listed most-recent-window first`() {
        val user = "order-user"
        val early = reserveReturningId(createCar(), user, 1, 2)
        val late = reserveReturningId(createCar(), user, 9, 10)
        val mid = reserveReturningId(createCar(), user, 5, 6)

        val ids = JsonPath.read<List<String>>(
            mockMvc.get("/api/reservations") { header("X-User-Id", user) }
                .andExpect { status { isOk() } }
                .andReturn().response.contentAsString,
            "$.content[*].id",
        )

        assertThat(ids).containsExactly(late, mid, early)
    }

    private fun rent(id: String, user: String) =
        mockMvc.post("/api/reservations/$id/rent") { header("X-User-Id", user) }

    @Test
    fun `returning a still-reserved booking returns 409`() {
        val id = reserveReturningId(createCar(), "alice", 0, 2)
        mockMvc.post("/api/reservations/$id/return") { header("X-User-Id", "alice") }
            .andExpect {
                status { isConflict() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("reservation-not-rented") }
            }
    }

    @Test
    fun `cancelling a rented booking returns 409`() {
        val id = reserveReturningId(createCar(), "alice", 0, 2)
        rent(id, "alice").andExpect { status { isOk() } }
        mockMvc.post("/api/reservations/$id/cancel") { header("X-User-Id", "alice") }
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("reservation-not-cancellable") }
            }
    }

    @Test
    fun `renting a booking twice returns 409`() {
        val id = reserveReturningId(createCar(), "alice", 0, 2)
        rent(id, "alice").andExpect { status { isOk() } }
        rent(id, "alice").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("reservation-not-reserved") }
        }
    }

    @Test
    fun `returning a booking twice returns 409`() {
        val id = reserveReturningId(createCar(), "alice", 0, 2)
        rent(id, "alice").andExpect { status { isOk() } }
        mockMvc.post("/api/reservations/$id/return") { header("X-User-Id", "alice") }
            .andExpect { status { isOk() } }
        mockMvc.post("/api/reservations/$id/return") { header("X-User-Id", "alice") }
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `returning a reservation frees the slot for re-booking`() {
        val carId = createCar()
        val id = reserveReturningId(carId, "alice", 0, 2)
        rent(id, "alice").andExpect { status { isOk() } }

        reserve(carId, "bob", 0, 2).andExpect { status { isConflict() } }

        mockMvc.post("/api/reservations/$id/return") { header("X-User-Id", "alice") }
            .andExpect { status { isOk() } }

        reserve(carId, "bob", 0, 2).andExpect { status { isCreated() } }
    }

    @Test
    fun `malformed json body returns 400`() {
        mockMvc.post("/api/cars") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"make":"Tesla","model":"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `getting an unknown car returns 404 problem json`() {
        mockMvc.get("/api/cars/${UUID.randomUUID()}").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("car-not-found") }
        }
    }

    @Test
    fun `non-uuid car id returns 400`() {
        mockMvc.get("/api/cars/not-a-uuid").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `unknown sort property returns 400 problem json`() {
        mockMvc.get("/api/cars?sort=bogus").andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("invalid-sort") }
        }
    }

    @Test
    fun `business validation error carries a problem json detail and code`() {
        val carId = createCar()
        mockMvc.post("/api/reservations") {
            header("X-User-Id", "alice")
            contentType = MediaType.APPLICATION_JSON
            content = reservationBody(carId, 3, 1)
        }.andExpect {
            status { isUnprocessableContent() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { exists() }
            jsonPath("$.code") { value("invalid-rental-period") }
        }
    }

    @Test
    fun `rescheduling frees the old window and occupies the new one`() {
        val carId = createCar()
        val id = reserveReturningId(carId, "alice", 1, 3)

        reschedule(id, "alice", 5, 7).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("RESERVED") }
        }

        reserve(carId, "bob", 1, 3).andExpect { status { isCreated() } }
        reserve(carId, "carol", 5, 7).andExpect { status { isConflict() } }
    }

    @Test
    fun `rescheduling into a window overlapping its own current window succeeds`() {
        val carId = createCar()
        val id = reserveReturningId(carId, "alice", 1, 3)

        reschedule(id, "alice", 2, 4).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("RESERVED") }
        }

        reserve(carId, "bob", 3, 4).andExpect { status { isConflict() } }
        reserve(carId, "bob", 1, 2).andExpect { status { isCreated() } }
    }

    @Test
    fun `rescheduling into another active booking returns 409`() {
        val carId = createCar()
        reserveReturningId(carId, "alice", 1, 3)
        val second = reserveReturningId(carId, "alice", 5, 7)

        reschedule(second, "alice", 2, 4).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("overlapping-reservation") }
        }
    }

    @Test
    fun `rescheduling a rented booking returns 409`() {
        val id = reserveReturningId(createCar(), "alice", 0, 2)
        rent(id, "alice").andExpect { status { isOk() } }

        reschedule(id, "alice", 3, 5).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("reservation-not-reschedulable") }
        }
    }

    @Test
    fun `another user cannot reschedule the reservation`() {
        val id = reserveReturningId(createCar(), "alice", 1, 3)
        reschedule(id, "bob", 5, 7).andExpect { status { isNotFound() } }
    }
}
