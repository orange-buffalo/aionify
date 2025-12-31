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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * API endpoint security tests for time log entry resource.
 *
 * Per project guidelines, this test validates SECURITY ONLY, not business logic.
 *
 * Tests verify:
 * 1. Authentication is required to access time log entry endpoints
 * 2. Users can only access their own time log entries
 * 3. Users cannot modify or delete other users' time log entries
 */
@MicronautTest(transactional = false)
class TimeLogEntryResourceTest {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var timeEntryRepository: TimeLogEntryRepository

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    @Inject
    lateinit var testUsers: TestUsers

    private lateinit var user1: User
    private lateinit var user2: User
    private lateinit var user1Entry: TimeLogEntry
    private lateinit var user2Entry: TimeLogEntry

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()

        // Create two users
        user1 = testUsers.createRegularUser("user1", "User One")
        user2 = testUsers.createRegularUser("user2", "User Two")

        // Create time entries for both users
        user1Entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = Instant.parse("2024-01-15T10:00:00Z"),
                    endTime = Instant.parse("2024-01-15T11:00:00Z"),
                    title = "User 1 Task",
                    ownerId = requireNotNull(user1.id),
                ),
            )

        user2Entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = Instant.parse("2024-01-15T14:00:00Z"),
                    endTime = null,
                    title = "User 2 Task",
                    ownerId = requireNotNull(user2.id),
                ),
            )
    }

    @Test
    fun `should require authentication to list time entries`() {
        // When: Trying to access endpoint without authentication
        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(
                    HttpRequest.GET<Any>("/api-ui/time-log-entries?startTime=2024-01-15T00:00:00Z&endTime=2024-01-22T00:00:00Z"),
                    String::class.java,
                )
            }

        // Then: Access should be unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should require authentication to get active entry`() {
        // When: Trying to access endpoint without authentication
        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(
                    HttpRequest.GET<Any>("/api-ui/time-log-entries/active"),
                    String::class.java,
                )
            }

        // Then: Access should be unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should require authentication to create time entry`() {
        // When: Trying to create without authentication
        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(
                    HttpRequest.POST("/api-ui/time-log-entries", mapOf("title" to "Test Task")),
                    String::class.java,
                )
            }

        // Then: Access should be unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should only return own time entries`() {
        // Given: User 1 token
        val user1Token = testAuthSupport.generateToken(user1)

        // When: User 1 lists time entries
        val response =
            client.toBlocking().exchange(
                HttpRequest
                    .GET<Any>("/api-ui/time-log-entries?startTime=2024-01-15T00:00:00Z&endTime=2024-01-22T00:00:00Z")
                    .bearerAuth(user1Token),
                TimeLogEntriesResponse::class.java,
            )

        // Then: Should only see own entries
        assertEquals(HttpStatus.OK, response.status)
        val entries = response.body()?.entries ?: emptyList()
        assertEquals(1, entries.size)
        assertEquals("User 1 Task", entries[0].title)
        assertEquals(user1.id, entries[0].ownerId)
    }

    @Test
    fun `should not allow stopping other users entries`() {
        // Given: User 1 token and User 2's entry
        val user1Token = testAuthSupport.generateToken(user1)

        // When: User 1 tries to stop User 2's entry
        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(
                    HttpRequest
                        .PUT("/api-ui/time-log-entries/${user2Entry.id}/stop", emptyMap<String, Any>())
                        .bearerAuth(user1Token),
                    String::class.java,
                )
            }

        // Then: Should be not found (entry belongs to different user)
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `should not allow deleting other users entries`() {
        // Given: User 1 token and User 2's entry
        val user1Token = testAuthSupport.generateToken(user1)

        // When: User 1 tries to delete User 2's entry
        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(
                    HttpRequest
                        .DELETE<Any>("/api-ui/time-log-entries/${user2Entry.id}")
                        .bearerAuth(user1Token),
                    String::class.java,
                )
            }

        // Then: Should be not found (entry belongs to different user)
        assertEquals(HttpStatus.NOT_FOUND, exception.status)

        // And: Entry should still exist
        val stillExists = timeEntryRepository.findById(requireNotNull(user2Entry.id))
        assertTrue(stillExists.isPresent)
    }

    @Test
    fun `should allow user to stop own entry`() {
        // Given: User 2 token and User 2's active entry
        val user2Token = testAuthSupport.generateToken(user2)

        // When: User 2 stops their own entry
        val response =
            client.toBlocking().exchange(
                HttpRequest
                    .PUT("/api-ui/time-log-entries/${user2Entry.id}/stop", emptyMap<String, Any>())
                    .bearerAuth(user2Token),
                TimeLogEntryDto::class.java,
            )

        // Then: Should succeed
        assertEquals(HttpStatus.OK, response.status)
        val stoppedEntry = response.body()
        assertNotNull(stoppedEntry)
        assertNotNull(stoppedEntry?.endTime)
    }

    @Test
    fun `should allow user to delete own entry`() {
        // Given: User 1 token and User 1's entry
        val user1Token = testAuthSupport.generateToken(user1)

        // When: User 1 deletes their own entry
        val response =
            client.toBlocking().exchange(
                HttpRequest
                    .DELETE<Any>("/api-ui/time-log-entries/${user1Entry.id}")
                    .bearerAuth(user1Token),
                String::class.java,
            )

        // Then: Should succeed
        assertEquals(HttpStatus.OK, response.status)

        // And: Entry should be deleted
        val deleted = timeEntryRepository.findById(requireNotNull(user1Entry.id))
        assertFalse(deleted.isPresent)
    }

    @Test
    fun `should return own active entry only`() {
        // Given: User 2 token (who has an active entry)
        val user2Token = testAuthSupport.generateToken(user2)

        // When: User 2 gets active entry
        val response =
            client.toBlocking().exchange(
                HttpRequest
                    .GET<Any>("/api-ui/time-log-entries/active")
                    .bearerAuth(user2Token),
                ActiveLogEntryResponse::class.java,
            )

        // Then: Should see own active entry
        assertEquals(HttpStatus.OK, response.status)
        val activeEntry = response.body()?.entry
        assertNotNull(activeEntry)
        assertEquals("User 2 Task", activeEntry?.title)
        assertEquals(user2.id, activeEntry?.ownerId)
    }

    @Test
    fun `should return null active entry when user has none`() {
        // Given: User 1 token (who has no active entry)
        val user1Token = testAuthSupport.generateToken(user1)

        // When: User 1 gets active entry
        val response =
            client.toBlocking().exchange(
                HttpRequest
                    .GET<Any>("/api-ui/time-log-entries/active")
                    .bearerAuth(user1Token),
                ActiveLogEntryResponse::class.java,
            )

        // Then: Should return null entry
        assertEquals(HttpStatus.OK, response.status)
        val activeEntry = response.body()?.entry
        assertNull(activeEntry)
    }
}
