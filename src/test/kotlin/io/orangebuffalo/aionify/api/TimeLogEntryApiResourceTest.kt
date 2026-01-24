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
import java.time.Instant

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
            assertEquals("Working on API", response.body()?.title)
            assertEquals(emptyList<String>(), response.body()?.metadata)

            // And: Entry is saved in database
            val activeEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(testUser1.id!!).orElse(null)
                }
            assertNotNull(activeEntry)
            assertEquals("Working on API", activeEntry?.title)
            assertEquals(testUser1.id, activeEntry?.ownerId)
            assertNull(activeEntry?.endTime)
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
            assertEquals(emptyList<String>(), response.body()?.metadata)

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
            assertEquals(emptyList<String>(), response.body()?.metadata)
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

        @Test
        fun `should start a new time log entry with metadata`() {
            // When: Starting a new entry with metadata
            val request =
                HttpRequest
                    .POST(
                        "/api/time-log-entries/start",
                        StartTimeLogEntryRequest(
                            title = "Working on API",
                            metadata = listOf("project:aionify", "task:API-123"),
                        ),
                    ).bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)

            // Then: Request succeeds
            assertEquals(HttpStatus.OK, response.status)
            assertEquals("Working on API", response.body()?.title)
            assertEquals(listOf("project:aionify", "task:API-123"), response.body()?.metadata)

            // And: Entry is saved in database with metadata
            val activeEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(testUser1.id!!).orElse(null)
                }
            assertNotNull(activeEntry)
            assertEquals("Working on API", activeEntry?.title)
            assertArrayEquals(arrayOf("project:aionify", "task:API-123"), activeEntry?.metadata)
        }

        @Test
        fun `should start a new time log entry with empty metadata when not provided`() {
            // When: Starting a new entry without metadata
            val request =
                HttpRequest
                    .POST(
                        "/api/time-log-entries/start",
                        StartTimeLogEntryRequest(title = "Working on API"),
                    ).bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)

            // Then: Request succeeds
            assertEquals(HttpStatus.OK, response.status)
            assertEquals("Working on API", response.body()?.title)
            assertEquals(emptyList<String>(), response.body()?.metadata)

            // And: Entry is saved in database with empty metadata
            val activeEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(testUser1.id!!).orElse(null)
                }
            assertNotNull(activeEntry)
            assertEquals("Working on API", activeEntry?.title)
            assertArrayEquals(emptyArray<String>(), activeEntry?.metadata)
        }

        @Test
        fun `should start a new time log entry with explicit empty metadata`() {
            // When: Starting a new entry with explicit empty metadata
            val request =
                HttpRequest
                    .POST(
                        "/api/time-log-entries/start",
                        StartTimeLogEntryRequest(title = "Working on API", metadata = emptyList()),
                    ).bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)

            // Then: Request succeeds
            assertEquals(HttpStatus.OK, response.status)
            assertEquals("Working on API", response.body()?.title)
            assertEquals(emptyList<String>(), response.body()?.metadata)

            // And: Entry is saved in database with empty metadata
            val activeEntry =
                testDatabaseSupport.inTransaction {
                    timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(testUser1.id!!).orElse(null)
                }
            assertNotNull(activeEntry)
            assertArrayEquals(emptyArray<String>(), activeEntry?.metadata)
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

            // When: Getting active entry
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries/active")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, TimeLogEntryApiDto::class.java)

            // Then: Request succeeds
            assertEquals(HttpStatus.OK, response.status)
            assertEquals("Active Entry", response.body()?.title)
            assertEquals(emptyList<String>(), response.body()?.metadata)
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
                    client.toBlocking().exchange(request, TimeLogEntryApiDto::class.java)
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
                    client.toBlocking().exchange(request, TimeLogEntryApiDto::class.java)
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

            val response1 = client.toBlocking().exchange(request1, TimeLogEntryApiDto::class.java)

            // Then: User1 gets their own entry
            assertEquals(HttpStatus.OK, response1.status)
            assertEquals("User1 Active Entry", response1.body()?.title)
            assertEquals(emptyList<String>(), response1.body()?.metadata)

            // When: User2 gets their active entry
            val request2 =
                HttpRequest
                    .GET<Any>("/api/time-log-entries/active")
                    .bearerAuth(validToken2)

            val response2 = client.toBlocking().exchange(request2, TimeLogEntryApiDto::class.java)

            // Then: User2 gets their own entry
            assertEquals(HttpStatus.OK, response2.status)
            assertEquals("User2 Active Entry", response2.body()?.title)
            assertEquals(emptyList<String>(), response2.body()?.metadata)
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
                    client.toBlocking().exchange(request, TimeLogEntryApiDto::class.java)
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
                    client.toBlocking().exchange(request, TimeLogEntryApiDto::class.java)
                }

            // Then: Returns 404 (stopped entry is not active)
            assertEquals(HttpStatus.NOT_FOUND, exception.status)
        }

        @Test
        fun `should return active entry with metadata`() {
            // Given: User has an active entry with metadata
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = timeService.now(),
                        endTime = null,
                        title = "Active Entry",
                        ownerId = testUser1.id!!,
                        metadata = arrayOf("project:aionify", "task:API-123"),
                    ),
                )
            }

            // When: Getting active entry
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries/active")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, TimeLogEntryApiDto::class.java)

            // Then: Request succeeds with metadata
            assertEquals(HttpStatus.OK, response.status)
            assertEquals("Active Entry", response.body()?.title)
            assertEquals(listOf("project:aionify", "task:API-123"), response.body()?.metadata)
        }

        @Test
        fun `should return active entry with empty metadata`() {
            // Given: User has an active entry with empty metadata
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = timeService.now(),
                        endTime = null,
                        title = "Active Entry",
                        ownerId = testUser1.id!!,
                        metadata = emptyArray(),
                    ),
                )
            }

            // When: Getting active entry
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries/active")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, TimeLogEntryApiDto::class.java)

            // Then: Request succeeds with empty metadata
            assertEquals(HttpStatus.OK, response.status)
            assertEquals("Active Entry", response.body()?.title)
            assertEquals(emptyList<String>(), response.body()?.metadata)
        }
    }

    @Nested
    inner class ListEntriesEndpoint {
        @Test
        fun `should list entries in time range`() {
            // Given: User has multiple entries in and out of range
            testDatabaseSupport.inTransaction {
                // Entry outside range (too old)
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = Instant.parse("2026-01-10T10:00:00Z"), // Way before range
                        endTime = Instant.parse("2026-01-10T11:00:00Z"),
                        title = "Entry Too Old",
                        ownerId = testUser1.id!!,
                    ),
                )
                // Entry 1 in range
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = Instant.parse("2026-01-15T10:00:00Z"), // 2 hours ago
                        endTime = Instant.parse("2026-01-15T11:00:00Z"), // 1 hour ago
                        title = "Entry 1",
                        ownerId = testUser1.id!!,
                        tags = arrayOf("tag1"),
                        metadata = arrayOf("meta1"),
                    ),
                )
                // Entry 2 in range
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = Instant.parse("2026-01-15T11:00:00Z"), // 1 hour ago
                        endTime = Instant.parse("2026-01-15T11:30:00Z"), // 30 mins ago
                        title = "Entry 2",
                        ownerId = testUser1.id!!,
                        tags = arrayOf("tag2"),
                        metadata = arrayOf("meta2"),
                    ),
                )
                // Entry outside range (too new)
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = Instant.parse("2026-01-20T10:00:00Z"), // After range
                        endTime = Instant.parse("2026-01-20T11:00:00Z"),
                        title = "Entry Too New",
                        ownerId = testUser1.id!!,
                    ),
                )
            }

            // When: Listing entries - using predefined ISO strings for clarity
            val startTimeFrom = "2026-01-15T00:00:00Z"
            val startTimeTo = "2026-01-16T00:00:00Z"
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)

            // Then: Request succeeds
            assertEquals(HttpStatus.OK, response.status)
            val body = response.body()!!
            assertEquals(2, body.totalElements)
            assertEquals(0, body.page)
            assertEquals(100, body.size)
            assertEquals(2, body.entries.size)

            // Entries should be ordered by start time descending (newest first)
            assertEquals("Entry 2", body.entries[0].title)
            assertEquals(listOf("tag2"), body.entries[0].tags)
            assertEquals(listOf("meta2"), body.entries[0].metadata)
            assertEquals("Entry 1", body.entries[1].title)
            assertEquals(listOf("tag1"), body.entries[1].tags)
            assertEquals(listOf("meta1"), body.entries[1].metadata)

            // Verify only entries in range are returned (not "Entry Too Old" or "Entry Too New")
            val returnedTitles = body.entries.map { it.title }
            assertFalse(returnedTitles.contains("Entry Too Old"))
            assertFalse(returnedTitles.contains("Entry Too New"))
        }

        @Test
        fun `should paginate results correctly`() {
            // Given: User has many entries
            testDatabaseSupport.inTransaction {
                for (i in 1..10) {
                    val hour = String.format("%02d", 10 + i)
                    timeLogEntryRepository.save(
                        TimeLogEntry(
                            startTime = Instant.parse("2026-01-15T$hour:00:00Z"),
                            endTime = Instant.parse("2026-01-15T$hour:05:00Z"),
                            title = "Entry $i",
                            ownerId = testUser1.id!!,
                        ),
                    )
                }
            }

            // When: Fetching first page with pageSize 3 - using predefined ISO strings
            val startTimeFrom = "2026-01-15T00:00:00Z"
            val startTimeTo = "2026-01-16T00:00:00Z"
            val request1 =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo&page=0&pageSize=3")
                    .bearerAuth(validToken1)

            val response1 = client.toBlocking().exchange(request1, ListTimeLogEntriesResponse::class.java)

            // Then: First page has correct data
            assertEquals(HttpStatus.OK, response1.status)
            val body1 = response1.body()!!
            assertEquals(10, body1.totalElements)
            assertEquals(0, body1.page)
            assertEquals(3, body1.size)
            assertEquals(3, body1.entries.size)
            // Entries are sorted by start time descending (newest first), so Entry 10, 9, 8
            assertEquals("Entry 10", body1.entries[0].title)
            assertEquals("Entry 9", body1.entries[1].title)
            assertEquals("Entry 8", body1.entries[2].title)

            // When: Fetching second page
            val request2 =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo&page=1&pageSize=3")
                    .bearerAuth(validToken1)

            val response2 = client.toBlocking().exchange(request2, ListTimeLogEntriesResponse::class.java)

            // Then: Second page has correct data
            assertEquals(HttpStatus.OK, response2.status)
            val body2 = response2.body()!!
            assertEquals(10, body2.totalElements)
            assertEquals(1, body2.page)
            assertEquals(3, body2.size)
            assertEquals(3, body2.entries.size)
            assertEquals("Entry 7", body2.entries[0].title)
            assertEquals("Entry 6", body2.entries[1].title)
            assertEquals("Entry 5", body2.entries[2].title)

            // When: Fetching last page
            val request3 =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo&page=3&pageSize=3")
                    .bearerAuth(validToken1)

            val response3 = client.toBlocking().exchange(request3, ListTimeLogEntriesResponse::class.java)

            // Then: Last page has correct data (only 1 entry)
            assertEquals(HttpStatus.OK, response3.status)
            val body3 = response3.body()!!
            assertEquals(10, body3.totalElements)
            assertEquals(3, body3.page)
            assertEquals(3, body3.size)
            assertEquals(1, body3.entries.size)
            assertEquals("Entry 1", body3.entries[0].title)
        }

        @Test
        fun `should return empty page when requesting beyond last page`() {
            // Given: User has 2 entries
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = Instant.parse("2026-01-15T10:00:00Z"),
                        endTime = Instant.parse("2026-01-15T10:30:00Z"),
                        title = "Entry 1",
                        ownerId = testUser1.id!!,
                    ),
                )
            }

            // When: Fetching page 5 - using predefined ISO strings
            val startTimeFrom = "2026-01-15T00:00:00Z"
            val startTimeTo = "2026-01-16T00:00:00Z"
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo&page=5&pageSize=100")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)

            // Then: Returns empty page
            assertEquals(HttpStatus.OK, response.status)
            val body = response.body()!!
            assertEquals(1, body.totalElements)
            assertEquals(5, body.page)
            assertEquals(100, body.size)
            assertEquals(0, body.entries.size)
        }

        @Test
        fun `should return empty results when no entries in range`() {
            // Given: User has entries outside the time range
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = Instant.parse("2026-01-20T10:00:00Z"), // 1 hour in future
                        endTime = Instant.parse("2026-01-20T12:00:00Z"),
                        title = "Future Entry",
                        ownerId = testUser1.id!!,
                    ),
                )
            }

            // When: Querying past time range - using predefined ISO strings
            val startTimeFrom = "2026-01-10T00:00:00Z"
            val startTimeTo = "2026-01-12T00:00:00Z"
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)

            // Then: Returns empty results
            assertEquals(HttpStatus.OK, response.status)
            val body = response.body()!!
            assertEquals(0, body.totalElements)
            assertEquals(0, body.page)
            assertEquals(100, body.size)
            assertEquals(0, body.entries.size)
        }

        @Test
        fun `should only return current user entries`() {
            // Given: Both users have entries
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = Instant.parse("2026-01-15T10:00:00Z"),
                        endTime = Instant.parse("2026-01-15T10:30:00Z"),
                        title = "User1 Entry",
                        ownerId = testUser1.id!!,
                    ),
                )
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = Instant.parse("2026-01-15T10:00:00Z"),
                        endTime = Instant.parse("2026-01-15T10:30:00Z"),
                        title = "User2 Entry",
                        ownerId = testUser2.id!!,
                    ),
                )
            }

            // When: User1 lists entries - using predefined ISO strings
            val startTimeFrom = "2026-01-15T00:00:00Z"
            val startTimeTo = "2026-01-16T00:00:00Z"
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)

            // Then: Only User1's entries are returned
            assertEquals(HttpStatus.OK, response.status)
            val body = response.body()!!
            assertEquals(1, body.totalElements)
            assertEquals(1, body.entries.size)
            assertEquals("User1 Entry", body.entries[0].title)
        }

        @Test
        fun `should reject request without authentication`() {
            // When: Listing entries without token - using predefined ISO strings
            val startTimeFrom = "2026-01-15T00:00:00Z"
            val startTimeTo = "2026-01-16T00:00:00Z"
            val request =
                HttpRequest.GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo")

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)
                }

            // Then: Request is rejected with 401
            assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        }

        @Test
        fun `should reject invalid time range`() {
            // When: Providing invalid time range (startTimeFrom after startTimeTo) - using predefined ISO strings
            val startTimeFrom = "2026-01-16T00:00:00Z"
            val startTimeTo = "2026-01-15T00:00:00Z"
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo")
                    .bearerAuth(validToken1)

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)
                }

            // Then: Request is rejected with 400
            assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        }

        @Test
        fun `should reject equal start and end times`() {
            // When: Providing equal start and end times - using predefined ISO strings
            val timestamp = "2026-01-15T12:00:00Z"
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$timestamp&startTimeTo=$timestamp")
                    .bearerAuth(validToken1)

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)
                }

            // Then: Request is rejected with 400
            assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        }

        @Test
        fun `should respect max page size limit`() {
            // Given: User has many entries
            testDatabaseSupport.inTransaction {
                for (i in 1..600) {
                    // Create timestamps that are properly formatted
                    val startTime = Instant.parse("2026-01-15T10:00:00Z").plusSeconds(i * 10L)
                    val endTime = startTime.plusSeconds(5)
                    timeLogEntryRepository.save(
                        TimeLogEntry(
                            startTime = startTime,
                            endTime = endTime,
                            title = "Entry $i",
                            ownerId = testUser1.id!!,
                        ),
                    )
                }
            }

            // When: Requesting with pageSize > 500 - using predefined ISO strings
            val startTimeFrom = "2026-01-15T00:00:00Z"
            val startTimeTo = "2026-01-16T00:00:00Z"
            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    val request =
                        HttpRequest
                            .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo&pageSize=501")
                            .bearerAuth(validToken1)
                    client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)
                }

            // Then: Request is rejected with 400 (validation error)
            assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        }

        @Test
        fun `should allow max page size of 500`() {
            // Given: User has many entries
            testDatabaseSupport.inTransaction {
                for (i in 1..600) {
                    // Create timestamps that are properly formatted
                    val startTime = Instant.parse("2026-01-15T10:00:00Z").plusSeconds(i * 10L)
                    val endTime = startTime.plusSeconds(5)
                    timeLogEntryRepository.save(
                        TimeLogEntry(
                            startTime = startTime,
                            endTime = endTime,
                            title = "Entry $i",
                            ownerId = testUser1.id!!,
                        ),
                    )
                }
            }

            // When: Requesting with pageSize = 500 - using predefined ISO strings
            val startTimeFrom = "2026-01-15T00:00:00Z"
            val startTimeTo = "2026-01-16T00:00:00Z"
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo&pageSize=500")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)

            // Then: Request succeeds with 500 entries
            assertEquals(HttpStatus.OK, response.status)
            val body = response.body()!!
            assertEquals(600, body.totalElements)
            assertEquals(0, body.page)
            assertEquals(500, body.size)
            assertEquals(500, body.entries.size)
        }

        @Test
        fun `should include active entries without end time`() {
            // Given: User has active and stopped entries
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = Instant.parse("2026-01-15T10:00:00Z"),
                        endTime = Instant.parse("2026-01-15T10:30:00Z"),
                        title = "Stopped Entry",
                        ownerId = testUser1.id!!,
                    ),
                )
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = Instant.parse("2026-01-15T10:30:00Z"),
                        endTime = null,
                        title = "Active Entry",
                        ownerId = testUser1.id!!,
                    ),
                )
            }

            // When: Listing entries - using predefined ISO strings
            val startTimeFrom = "2026-01-15T00:00:00Z"
            val startTimeTo = "2026-01-16T00:00:00Z"
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)

            // Then: Both entries are included
            assertEquals(HttpStatus.OK, response.status)
            val body = response.body()!!
            assertEquals(2, body.totalElements)
            assertEquals(2, body.entries.size)

            // Active entry should be first (newer start time)
            assertEquals("Active Entry", body.entries[0].title)
            assertNull(body.entries[0].endTime)
            assertEquals("Stopped Entry", body.entries[1].title)
            assertNotNull(body.entries[1].endTime)
        }

        @Test
        fun `should use default page size when not specified`() {
            // Given: User has entries
            testDatabaseSupport.inTransaction {
                for (i in 1..5) {
                    val hour = String.format("%02d", 10 + i)
                    timeLogEntryRepository.save(
                        TimeLogEntry(
                            startTime = Instant.parse("2026-01-15T$hour:00:00Z"),
                            endTime = Instant.parse("2026-01-15T$hour:05:00Z"),
                            title = "Entry $i",
                            ownerId = testUser1.id!!,
                        ),
                    )
                }
            }

            // When: Listing entries without size parameter - using predefined ISO strings
            val startTimeFrom = "2026-01-15T00:00:00Z"
            val startTimeTo = "2026-01-16T00:00:00Z"
            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$startTimeFrom&startTimeTo=$startTimeTo")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)

            // Then: Default size of 100 is used
            assertEquals(HttpStatus.OK, response.status)
            val body = response.body()!!
            assertEquals(100, body.size)
            assertEquals(5, body.totalElements)
        }

        @Test
        fun `should accept ISO date time format with timezone offset`() {
            // Given: User has entries at specific times
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.save(
                    TimeLogEntry(
                        startTime = Instant.parse("2026-01-15T10:00:00Z"),
                        endTime = Instant.parse("2026-01-15T11:00:00Z"),
                        title = "Test Entry",
                        ownerId = testUser1.id!!,
                    ),
                )
            }

            // When: Listing entries with ISO 8601 format including timezone offset
            // Using predefined strings to clearly show the format
            val startTimeFromStr = "2026-01-15T09:00:00+11:00" // 2026-01-14T22:00:00Z in UTC
            val startTimeToStr = "2026-01-15T23:00:00+11:00" // 2026-01-15T12:00:00Z in UTC

            // URL encode the parameters to handle special characters like + and :
            val encodedStartTimeFrom = java.net.URLEncoder.encode(startTimeFromStr, "UTF-8")
            val encodedStartTimeTo = java.net.URLEncoder.encode(startTimeToStr, "UTF-8")

            val request =
                HttpRequest
                    .GET<Any>("/api/time-log-entries?startTimeFrom=$encodedStartTimeFrom&startTimeTo=$encodedStartTimeTo")
                    .bearerAuth(validToken1)

            val response = client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)

            // Then: Request succeeds
            assertEquals(HttpStatus.OK, response.status)
            val body = response.body()!!
            assertEquals(1, body.totalElements)
            assertEquals("Test Entry", body.entries[0].title)
        }

        @Test
        fun `should reject invalid ISO date time format with meaningful error`() {
            // When: Providing invalid datetime format
            val invalidFormat = "not-a-valid-datetime"
            val encodedInvalidFormat = java.net.URLEncoder.encode(invalidFormat, "UTF-8")
            val validFormat = java.net.URLEncoder.encode("2026-01-15T10:00:00Z", "UTF-8")

            val exception =
                assertThrows(HttpClientResponseException::class.java) {
                    val request =
                        HttpRequest
                            .GET<Any>("/api/time-log-entries?startTimeFrom=$encodedInvalidFormat&startTimeTo=$validFormat")
                            .bearerAuth(validToken1)
                    client.toBlocking().exchange(request, ListTimeLogEntriesResponse::class.java)
                }

            // Then: Request is rejected with 400 and meaningful error message
            assertEquals(HttpStatus.BAD_REQUEST, exception.status)
            val errorBody = exception.response.getBody(String::class.java).orElse("")
            assertTrue(
                errorBody.contains("startTimeFrom") || errorBody.contains("Bad Request"),
                "Error message should mention the problematic parameter or be a Bad Request",
            )
        }
    }
}
