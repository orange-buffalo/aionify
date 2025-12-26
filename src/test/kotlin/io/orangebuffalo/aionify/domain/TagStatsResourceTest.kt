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
 * API endpoint security tests for tag stats resource.
 * 
 * Per project guidelines, this test validates SECURITY ONLY, not business logic.
 * 
 * Tests verify:
 * 1. Authentication is required to access tag stats endpoints
 * 2. Users can only access their own tag statistics
 * 3. Tag statistics are calculated only from the current user's entries
 */
@MicronautTest(transactional = false)
class TagStatsResourceTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var timeLogEntryRepository: TimeLogEntryRepository

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

        // Create two users
        user1 = testUsers.createRegularUser("user1", "User One")
        user2 = testUsers.createRegularUser("user2", "User Two")
    }

    @Test
    fun `should require authentication to access tag stats`() {
        // When: Trying to access endpoint without authentication
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("/api/tags/stats"),
                String::class.java
            )
        }

        // Then: Access should be unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should only return tag stats from own entries`() {
        // Given: User 1 has entries with tags, User 2 has entries with different tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "User 1 Task",
                ownerId = requireNotNull(user1.id),
                tags = listOf("kotlin", "backend")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T14:00:00Z"),
                endTime = null,
                title = "User 2 Task",
                ownerId = requireNotNull(user2.id),
                tags = listOf("react", "frontend")
            )
        )

        // When: User 1 requests tag stats
        val user1Token = testAuthSupport.generateToken(user1)
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/api/tags/stats")
                .bearerAuth(user1Token),
            TagStatsResponse::class.java
        )

        // Then: Should only see own tags
        assertEquals(HttpStatus.OK, response.status)
        val stats = response.body()?.tags ?: emptyList()
        assertEquals(2, stats.size)
        
        // Verify tags are sorted alphabetically
        assertEquals("backend", stats[0].tag)
        assertEquals(1L, stats[0].count)
        assertEquals("kotlin", stats[1].tag)
        assertEquals(1L, stats[1].count)
        
        // Verify User 2's tags are not included
        assertFalse(stats.any { it.tag == "react" })
        assertFalse(stats.any { it.tag == "frontend" })
    }

    @Test
    fun `should return empty list when user has no entries with tags`() {
        // Given: User 1 has no entries
        val user1Token = testAuthSupport.generateToken(user1)

        // When: User 1 requests tag stats
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/api/tags/stats")
                .bearerAuth(user1Token),
            TagStatsResponse::class.java
        )

        // Then: Should return empty list
        assertEquals(HttpStatus.OK, response.status)
        val stats = response.body()?.tags ?: emptyList()
        assertTrue(stats.isEmpty())
    }

    @Test
    fun `should return empty list when user has entries without tags`() {
        // Given: User 1 has entries but no tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "User 1 Task",
                ownerId = requireNotNull(user1.id),
                tags = emptyList()
            )
        )

        val user1Token = testAuthSupport.generateToken(user1)

        // When: User 1 requests tag stats
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/api/tags/stats")
                .bearerAuth(user1Token),
            TagStatsResponse::class.java
        )

        // Then: Should return empty list
        assertEquals(HttpStatus.OK, response.status)
        val stats = response.body()?.tags ?: emptyList()
        assertTrue(stats.isEmpty())
    }

    @Test
    fun `should count tag usage correctly across multiple entries`() {
        // Given: User 1 has multiple entries with overlapping tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task 1",
                ownerId = requireNotNull(user1.id),
                tags = listOf("kotlin", "backend")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T12:00:00Z"),
                endTime = Instant.parse("2024-01-15T13:00:00Z"),
                title = "Task 2",
                ownerId = requireNotNull(user1.id),
                tags = listOf("kotlin", "testing")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T14:00:00Z"),
                endTime = null,
                title = "Task 3",
                ownerId = requireNotNull(user1.id),
                tags = listOf("kotlin")
            )
        )

        val user1Token = testAuthSupport.generateToken(user1)

        // When: User 1 requests tag stats
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/api/tags/stats")
                .bearerAuth(user1Token),
            TagStatsResponse::class.java
        )

        // Then: Should show correct counts
        assertEquals(HttpStatus.OK, response.status)
        val stats = response.body()?.tags ?: emptyList()
        assertEquals(3, stats.size)
        
        // Verify counts (sorted alphabetically)
        assertEquals("backend", stats[0].tag)
        assertEquals(1L, stats[0].count)
        assertEquals("kotlin", stats[1].tag)
        assertEquals(3L, stats[1].count) // Used in all 3 entries
        assertEquals("testing", stats[2].tag)
        assertEquals(1L, stats[2].count)
    }

    @Test
    fun `should handle mix of entries with and without tags`() {
        // Given: User 1 has entries with and without tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Tagged Task",
                ownerId = requireNotNull(user1.id),
                tags = listOf("kotlin")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T12:00:00Z"),
                endTime = Instant.parse("2024-01-15T13:00:00Z"),
                title = "Untagged Task",
                ownerId = requireNotNull(user1.id),
                tags = emptyList()
            )
        )

        val user1Token = testAuthSupport.generateToken(user1)

        // When: User 1 requests tag stats
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/api/tags/stats")
                .bearerAuth(user1Token),
            TagStatsResponse::class.java
        )

        // Then: Should only include tags from tagged entries
        assertEquals(HttpStatus.OK, response.status)
        val stats = response.body()?.tags ?: emptyList()
        assertEquals(1, stats.size)
        assertEquals("kotlin", stats[0].tag)
        assertEquals(1L, stats[0].count)
    }

    @Test
    fun `should sort tags alphabetically`() {
        // Given: User 1 has entries with tags in non-alphabetical order
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task",
                ownerId = requireNotNull(user1.id),
                tags = listOf("zebra", "apple", "mango", "banana")
            )
        )

        val user1Token = testAuthSupport.generateToken(user1)

        // When: User 1 requests tag stats
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/api/tags/stats")
                .bearerAuth(user1Token),
            TagStatsResponse::class.java
        )

        // Then: Tags should be sorted alphabetically
        assertEquals(HttpStatus.OK, response.status)
        val stats = response.body()?.tags ?: emptyList()
        assertEquals(4, stats.size)
        assertEquals("apple", stats[0].tag)
        assertEquals("banana", stats[1].tag)
        assertEquals("mango", stats[2].tag)
        assertEquals("zebra", stats[3].tag)
    }

    @Test
    fun `should handle user with single entry using single tag`() {
        // Given: User 1 has one entry with one tag
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Single Tag Task",
                ownerId = requireNotNull(user1.id),
                tags = listOf("solo")
            )
        )

        val user1Token = testAuthSupport.generateToken(user1)

        // When: User 1 requests tag stats
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/api/tags/stats")
                .bearerAuth(user1Token),
            TagStatsResponse::class.java
        )

        // Then: Should return single tag with count 1
        assertEquals(HttpStatus.OK, response.status)
        val stats = response.body()?.tags ?: emptyList()
        assertEquals(1, stats.size)
        assertEquals("solo", stats[0].tag)
        assertEquals(1L, stats[0].count)
    }
}
