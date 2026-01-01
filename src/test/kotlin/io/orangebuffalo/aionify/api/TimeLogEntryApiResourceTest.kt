package io.orangebuffalo.aionify.api

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.TimeLogEntryRepository
import io.orangebuffalo.aionify.domain.TimeService
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import io.orangebuffalo.aionify.domain.UserApiAccessTokenRepository
import io.orangebuffalo.aionify.domain.UserRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

/**
 * Tests for time log entry public API.
 */
@MicronautTest(transactional = false)
class TimeLogEntryApiResourceTest {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userApiAccessTokenRepository: UserApiAccessTokenRepository

    @Inject
    lateinit var timeLogEntryRepository: TimeLogEntryRepository

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    @Inject
    lateinit var timeService: TimeService

    @Inject
    lateinit var apiRateLimitingService: ApiRateLimitingService

    private lateinit var testUser1: User
    private lateinit var testUser2: User
    private lateinit var validToken1: String
    private lateinit var validToken2: String

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()

        // Create first test user
        testUser1 =
            testDatabaseSupport.insert(
                User.create(
                    userName = "user1",
                    passwordHash = BCrypt.hashpw("password", BCrypt.gensalt()),
                    greeting = "User 1",
                    isAdmin = false,
                    locale = java.util.Locale.US,
                ),
            )

        // Create API token for user1
        validToken1 = "test-api-token-user1"
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = testUser1.id!!,
                token = validToken1,
            ),
        )

        // Create second test user
        testUser2 =
            testDatabaseSupport.insert(
                User.create(
                    userName = "user2",
                    passwordHash = BCrypt.hashpw("password", BCrypt.gensalt()),
                    greeting = "User 2",
                    isAdmin = false,
                    locale = java.util.Locale.US,
                ),
            )

        // Create API token for user2
        validToken2 = "test-api-token-user2"
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = testUser2.id!!,
                token = validToken2,
            ),
        )

        // Clear any existing rate limit state
        apiRateLimitingService.clearAllAttempts()
    }

    @Nested
    inner class StartEndpoint {
        @Test
        fun `should start a new time log entry`() {
            // When: Starting a new entry
            val request =
                HttpRequest
                    .POST(
                        "/api/time-log-entries/start",
                        StartTimeLogEntryRequest(title = "Working on API"),
                    ).bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)

            // Then: Request succeeds
            assertEquals(HttpStatus.OK, response.status)
            val responseBody = response.body()!!
            assertEquals("Working on API", responseBody.title)
            assertNotNull(responseBody.id)
            assertNotNull(responseBody.startTime)
            assertEquals(emptyList<String>(), responseBody.tags)

            // And: Entry is saved in database
            val activeEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(testUser1.id!!).orElse(null)
                }
            assertNotNull(activeEntry)
            assertEquals("Working on API", activeEntry?.title)
            assertEquals(testUser1.id, activeEntry?.ownerId)
            assertNull(activeEntry?.endTime)
            assertEquals(responseBody.id, activeEntry?.id)
            assertEquals(responseBody.startTime, activeEntry?.startTime)
        }

        @Test
        fun `should auto-stop active entry when starting a new one`() {
            // Given: User has an active entry
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = timeService.now(),
                        endTime = null,
                        title = "Old Entry",
                        ownerId = testUser1.id!!,
                    ),
                )
            }

            // When: Starting a new entry
            val request =
                HttpRequest
                    .POST(
                        "/api/time-log-entries/start",
                        StartTimeLogEntryRequest(title = "New Entry"),
                    ).bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)

            // Then: Request succeeds
            assertEquals(HttpStatus.OK, response.status)
            assertEquals("New Entry", response.body()?.title)

            // And: Old entry is stopped
            val entries =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.findAllOrderById()
                }
            assertEquals(2, entries.size)
            val oldEntry = entries.first { it.title == "Old Entry" }
            assertNotNull(oldEntry.endTime, "Old entry should be stopped")

            // And: New entry is active
            val newEntry = entries.first { it.title == "New Entry" }
            assertNull(newEntry.endTime, "New entry should be active")
        }

        @Test
        fun `should reject request with blank title`() {
            // When: Starting an entry with blank title
            val request =
                HttpRequest
                    .POST(
                        "/api/time-log-entries/start",
                        StartTimeLogEntryRequest(title = ""),
                    ).bearerAuth(validToken1)

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)
                }

            // Then: Request is rejected with 400
            assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        }

        @Test
        fun `should reject request with title exceeding max length`() {
            // When: Starting an entry with title > 1000 characters
            val longTitle = "x".repeat(1001)
            val request =
                HttpRequest
                    .POST(
                        "/api/time-log-entries/start",
                        StartTimeLogEntryRequest(title = longTitle),
                    ).bearerAuth(validToken1)

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)
                }

            // Then: Request is rejected with 400
            assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        }

        @Test
        fun `should accept request with title at max length`() {
            // When: Starting an entry with title = 1000 characters
            val maxTitle = "x".repeat(1000)
            val request =
                HttpRequest
                    .POST(
                        "/api/time-log-entries/start",
                        StartTimeLogEntryRequest(title = maxTitle),
                    ).bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)

            // Then: Request succeeds
            assertEquals(HttpStatus.OK, response.status)
            assertEquals(maxTitle, response.body()?.title)
        }

        @Test
        fun `should reject request without authentication`() {
            // When: Starting an entry without token
            val request =
                HttpRequest.POST(
                    "/api/time-log-entries/start",
                    StartTimeLogEntryRequest(title = "Test"),
                )

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)
                }

            // Then: Request is rejected with 401
            assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        }

        @Test
        fun `should start entry with tags`() {
            // When: Starting an entry with tags
            val request =
                HttpRequest
                    .POST(
                        "/api/time-log-entries/start",
                        StartTimeLogEntryRequest(title = "Working on API", tags = listOf("backend", "api")),
                    ).bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)

            // Then: Request succeeds with tags
            assertEquals(HttpStatus.OK, response.status)
            val responseBody = response.body()!!
            assertEquals("Working on API", responseBody.title)
            assertEquals(listOf("backend", "api"), responseBody.tags)

            // And: Entry is saved with tags in database
            val activeEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(testUser1.id!!).orElse(null)
                }
            assertNotNull(activeEntry)
            assertEquals(listOf("backend", "api"), activeEntry?.tags?.toList())
        }

        @Test
        fun `should not affect other users entries`() {
            // Given: User2 has an active entry
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = timeService.now(),
                        endTime = null,
                        title = "User2 Active Entry",
                        ownerId = testUser2.id!!,
                    ),
                )
            }

            // When: User1 starts a new entry
            val request =
                HttpRequest
                    .POST(
                        "/api/time-log-entries/start",
                        StartTimeLogEntryRequest(title = "User1 Entry"),
                    ).bearerAuth(validToken1)

            client.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)

            // Then: User2's entry is still active (not auto-stopped)
            val user2ActiveEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(testUser2.id!!).orElse(null)
                }
            assertNotNull(user2ActiveEntry)
            assertEquals("User2 Active Entry", user2ActiveEntry?.title)
            assertNull(user2ActiveEntry?.endTime, "User2's entry should still be active")
        }
    }

    @Nested
    inner class StopEndpoint {
        @Test
        fun `should stop active entry`() {
            // Given: User has an active entry
            val activeEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.save(
                        TimeLogEntry(
                            startTime = timeService.now(),
                            endTime = null,
                            title = "Active Entry",
                            ownerId = testUser1.id!!,
                        ),
                    )
                }

            // When: Stopping the entry
            val request =
                HttpRequest
                    .POST<Any>("/api/time-log-entries/stop", null)
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, StopTimeLogEntryResponse::class.java)

            // Then: Request succeeds with stopped=true
            assertEquals(HttpStatus.OK, response.status)
            assertTrue(response.body()?.stopped == true)

            // And: Entry is stopped in database
            val stoppedEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.findById(activeEntry.id!!).orElse(null)
                }
            assertNotNull(stoppedEntry?.endTime, "Entry should be stopped")
        }

        @Test
        fun `should succeed when no active entry exists`() {
            // Given: User has no active entry

            // When: Stopping (no active entry exists)
            val request =
                HttpRequest
                    .POST<Any>("/api/time-log-entries/stop", null)
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, StopTimeLogEntryResponse::class.java)

            // Then: Request succeeds with stopped=false
            assertEquals(HttpStatus.OK, response.status)
            assertFalse(response.body()?.stopped == true)
        }

        @Test
        fun `should reject request without authentication`() {
            // When: Stopping without token
            val request = HttpRequest.POST<Any>("/api/time-log-entries/stop", null)

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(request, StopTimeLogEntryResponse::class.java)
                }

            // Then: Request is rejected with 401
            assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        }

        @Test
        fun `should not affect other users entries`() {
            // Given: Both users have active entries
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = timeService.now(),
                        endTime = null,
                        title = "User1 Active Entry",
                        ownerId = testUser1.id!!,
                    ),
                )
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = timeService.now(),
                        endTime = null,
                        title = "User2 Active Entry",
                        ownerId = testUser2.id!!,
                    ),
                )
            }

            // When: User1 stops their entry
            val request =
                HttpRequest
                    .POST<Any>("/api/time-log-entries/stop", null)
                    .bearerAuth(validToken1)

            client.toBlocking().exchange(request, StopTimeLogEntryResponse::class.java)

            // Then: User1's entry is stopped
            val user1ActiveEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(testUser1.id!!).orElse(null)
                }
            assertNull(user1ActiveEntry, "User1 should have no active entry")

            // And: User2's entry is still active
            val user2ActiveEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(testUser2.id!!).orElse(null)
                }
            assertNotNull(user2ActiveEntry)
            assertEquals("User2 Active Entry", user2ActiveEntry?.title)
        }
    }

    @Nested
    inner class GetActiveEndpoint {
        @Test
        fun `should get active entry`() {
            // Given: User has an active entry
            val savedEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.save(
                        TimeLogEntry(
                            startTime = timeService.now(),
                            endTime = null,
                            title = "Active Entry",
                            ownerId = testUser1.id!!,
                            tags = arrayOf("tag1", "tag2"),
                        ),
                    )
                }

            // When: Getting active entry
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries/active")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, ActiveTimeLogEntryResponse::class.java)

            // Then: Request succeeds with metadata
            assertEquals(HttpStatus.OK, response.status)
            val responseBody = response.body()!!
            assertEquals("Active Entry", responseBody.title)
            assertEquals(savedEntry.id, responseBody.id)
            assertEquals(savedEntry.startTime, responseBody.startTime)
            assertEquals(listOf("tag1", "tag2"), responseBody.tags)
        }

        @Test
        fun `should return 404 when no active entry exists`() {
            // Given: User has no active entry

            // When: Getting active entry
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries/active")
                    .bearerAuth(validToken1)

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(request, ActiveTimeLogEntryResponse::class.java)
                }

            // Then: Request returns 404
            assertEquals(HttpStatus.NOT_FOUND, exception.status)
        }

        @Test
        fun `should reject request without authentication`() {
            // When: Getting active entry without token
            val request = HttpRequest.GET<Any>("/api/time-log-entries/active")

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(request, ActiveTimeLogEntryResponse::class.java)
                }

            // Then: Request is rejected with 401
            assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        }

        @Test
        fun `should return only current user entry`() {
            // Given: Both users have active entries
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = timeService.now(),
                        endTime = null,
                        title = "User1 Active Entry",
                        ownerId = testUser1.id!!,
                    ),
                )
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = timeService.now(),
                        endTime = null,
                        title = "User2 Active Entry",
                        ownerId = testUser2.id!!,
                    ),
                )
            }

            // When: User1 gets their active entry
            val request1 =
                HttpRequest
                    .GET<Any>("/api/time-log-entries/active")
                    .bearerAuth(validToken1)

            val response1 = client.toBlocking().exchange(request1, ActiveTimeLogEntryResponse::class.java)

            // Then: User1 gets their own entry
            assertEquals(HttpStatus.OK, response1.status)
            assertEquals("User1 Active Entry", response1.body()?.title)

            // When: User2 gets their active entry
            val request2 =
                HttpRequest
                    .GET<Any>("/api/time-log-entries/active")
                    .bearerAuth(validToken2)

            val response2 = client.toBlocking().exchange(request2, ActiveTimeLogEntryResponse::class.java)

            // Then: User2 gets their own entry
            assertEquals(HttpStatus.OK, response2.status)
            assertEquals("User2 Active Entry", response2.body()?.title)
        }

        @Test
        fun `should return 404 when other user has active entry`() {
            // Given: User2 has an active entry, User1 does not
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = timeService.now(),
                        endTime = null,
                        title = "User2 Active Entry",
                        ownerId = testUser2.id!!,
                    ),
                )
            }

            // When: User1 gets their active entry
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries/active")
                    .bearerAuth(validToken1)

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(request, ActiveTimeLogEntryResponse::class.java)
                }

            // Then: User1 gets 404 (cannot see User2's entry)
            assertEquals(HttpStatus.NOT_FOUND, exception.status)
        }

        @Test
        fun `should not return stopped entries`() {
            // Given: User has a stopped entry
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = timeService.now().minusSeconds(3600),
                        endTime = timeService.now(),
                        title = "Stopped Entry",
                        ownerId = testUser1.id!!,
                    ),
                )
            }

            // When: User1 gets their active entry
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries/active")
                    .bearerAuth(validToken1)

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(request, ActiveTimeLogEntryResponse::class.java)
                }

            // Then: Returns 404 (stopped entry is not active)
            assertEquals(HttpStatus.NOT_FOUND, exception.status)
        }
    }
}
