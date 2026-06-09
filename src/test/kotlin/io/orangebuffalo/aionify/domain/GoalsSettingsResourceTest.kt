package io.orangebuffalo.aionify.domain

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestAuthSupport
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.TestUsers
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

@MicronautTest(transactional = false)
class GoalsSettingsResourceTest {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var goalsSettingsRepository: GoalsSettingsRepository

    @Inject
    lateinit var dailyGoalBreakRepository: DailyGoalBreakRepository

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    @Inject
    lateinit var testUsers: TestUsers

    private lateinit var user1: User
    private lateinit var user2: User

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()
        user1 = testUsers.createRegularUser("goals-user-1", "Goals User One")
        user2 = testUsers.createRegularUser("goals-user-2", "Goals User Two")
    }

    @Test
    fun `should require authentication to access goals settings`() {
        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(
                    HttpRequest.GET<Any>("/api-ui/users/goals-settings"),
                    String::class.java,
                )
            }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should return default settings when user has no saved goals settings`() {
        // Given: a user with default goals settings row
        val token = testAuthSupport.generateToken(user1)

        // When: requesting goals settings
        val response =
            client.toBlocking().exchange(
                HttpRequest
                    .GET<Any>("/api-ui/users/goals-settings")
                    .bearerAuth(token),
                GoalsSettingsResponse::class.java,
            )

        // Then: default values should be returned
        assertEquals(HttpStatus.OK, response.status)
        val body = response.body()!!
        assertFalse(body.dailyGoal.enabled)
        assertEquals(0, body.dailyGoal.goalMinutes)
        assertTrue(body.dailyGoal.typicalBreaks.isEmpty())
    }

    @Test
    fun `should only return current user goals settings`() {
        val persistedSettings =
            testDatabaseSupport.insert(
                GoalsSettings(
                    userId = requireNotNull(user2.id),
                    dailyEnabled = true,
                    dailyGoalMinutes = 450,
                ),
            )
        testDatabaseSupport.insert(
            DailyGoalBreak(
                goalsSettingsId = requireNotNull(persistedSettings.id),
                sortOrder = 0,
                fromTime = LocalTime.parse("12:00"),
                toTime = LocalTime.parse("12:30"),
            ),
        )

        val token = testAuthSupport.generateToken(user1)
        val response =
            client.toBlocking().exchange(
                HttpRequest
                    .GET<Any>("/api-ui/users/goals-settings")
                    .bearerAuth(token),
                GoalsSettingsResponse::class.java,
            )

        assertEquals(HttpStatus.OK, response.status)
        val body = response.body()!!
        assertFalse(body.dailyGoal.enabled)
        assertTrue(body.dailyGoal.typicalBreaks.isEmpty())
    }

    @Test
    fun `should persist goals settings and breaks`() {
        val token = testAuthSupport.generateToken(user1)

        val updateResponse =
            client.toBlocking().exchange(
                HttpRequest
                    .PUT(
                        "/api-ui/users/goals-settings",
                        UpdateGoalsSettingsRequest(
                            dailyGoal =
                                UpdateDailyGoalRequest(
                                    enabled = true,
                                    goalMinutes = 450,
                                    typicalBreaks =
                                        listOf(
                                            UpdateTypicalBreakRequest(from = LocalTime.parse("12:00"), to = LocalTime.parse("12:30")),
                                            UpdateTypicalBreakRequest(from = LocalTime.parse("15:00"), to = LocalTime.parse("15:15")),
                                        ),
                                ),
                        ),
                    ).bearerAuth(token),
                GoalsSettingsSuccessResponse::class.java,
            )

        assertEquals(HttpStatus.OK, updateResponse.status)

        testDatabaseSupport.inTransaction {
            val settings = goalsSettingsRepository.findByUserId(requireNotNull(user1.id)).orElseThrow()
            assertTrue(settings.dailyEnabled)
            assertEquals(450, settings.dailyGoalMinutes)

            val breaks = dailyGoalBreakRepository.findByGoalsSettingsIdOrderBySortOrderAsc(requireNotNull(settings.id))
            assertEquals(2, breaks.size)
            assertEquals(LocalTime.parse("12:00"), breaks[0].fromTime)
            assertEquals(LocalTime.parse("12:30"), breaks[0].toTime)
            assertEquals(LocalTime.parse("15:00"), breaks[1].fromTime)
            assertEquals(LocalTime.parse("15:15"), breaks[1].toTime)
        }

        val getResponse =
            client.toBlocking().exchange(
                HttpRequest
                    .GET<Any>("/api-ui/users/goals-settings")
                    .bearerAuth(token),
                GoalsSettingsResponse::class.java,
            )

        val body = getResponse.body()!!
        assertTrue(body.dailyGoal.enabled)
        assertEquals(450, body.dailyGoal.goalMinutes)
        assertEquals(2, body.dailyGoal.typicalBreaks.size)
        assertEquals("12:00", body.dailyGoal.typicalBreaks[0].from)
        assertEquals("12:30", body.dailyGoal.typicalBreaks[0].to)
    }

    @Test
    fun `should reject invalid daily goal`() {
        val token = testAuthSupport.generateToken(user1)

        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(
                    HttpRequest
                        .PUT(
                            "/api-ui/users/goals-settings",
                            UpdateGoalsSettingsRequest(
                                dailyGoal =
                                    UpdateDailyGoalRequest(
                                        enabled = true,
                                        goalMinutes = 0,
                                    ),
                            ),
                        ).bearerAuth(token),
                    GoalsSettingsErrorResponse::class.java,
                )
            }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        val body = exception.response.getBody(GoalsSettingsErrorResponse::class.java).orElseThrow()
        assertEquals("INVALID_DAILY_GOAL", body.errorCode)
    }

    @Test
    fun `should reject overlapping typical breaks`() {
        val token = testAuthSupport.generateToken(user1)

        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(
                    HttpRequest
                        .PUT(
                            "/api-ui/users/goals-settings",
                            UpdateGoalsSettingsRequest(
                                dailyGoal =
                                    UpdateDailyGoalRequest(
                                        enabled = true,
                                        goalMinutes = 480,
                                        typicalBreaks =
                                            listOf(
                                                UpdateTypicalBreakRequest(from = LocalTime.parse("12:00"), to = LocalTime.parse("12:30")),
                                                UpdateTypicalBreakRequest(from = LocalTime.parse("12:15"), to = LocalTime.parse("12:45")),
                                            ),
                                    ),
                            ),
                        ).bearerAuth(token),
                    GoalsSettingsErrorResponse::class.java,
                )
            }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        val body = exception.response.getBody(GoalsSettingsErrorResponse::class.java).orElseThrow()
        assertEquals("INVALID_TYPICAL_BREAK", body.errorCode)
    }
}
