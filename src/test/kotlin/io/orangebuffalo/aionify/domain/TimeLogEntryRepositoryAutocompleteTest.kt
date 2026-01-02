package io.orangebuffalo.aionify.domain

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.TestUsers
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Tests for TimeLogEntryRepository autocomplete search functionality.
 */
@MicronautTest(transactional = false)
class TimeLogEntryRepositoryAutocompleteTest {
    @Inject
    lateinit var timeLogEntryRepository: TimeLogEntryRepository

    @Inject
    lateinit var testUsers: TestUsers

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    @BeforeEach
    fun setupTest() {
        // Clean up is handled by @MicronautTest
    }

    @Test
    fun `searchByTitleTokens should find entries by single token`() {
        val user = testUsers.createRegularUser()

        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = Instant.parse("2024-03-15T09:00:00Z"),
                    endTime = Instant.parse("2024-03-15T10:00:00Z"),
                    ownerId = user.id!!,
                    tags = arrayOf("work"),
                ),
            )

            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Team standup",
                    startTime = Instant.parse("2024-03-15T11:00:00Z"),
                    endTime = Instant.parse("2024-03-15T11:15:00Z"),
                    ownerId = user.id!!,
                    tags = arrayOf("meeting"),
                ),
            )

            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Code review",
                    startTime = Instant.parse("2024-03-15T13:00:00Z"),
                    endTime = Instant.parse("2024-03-15T14:00:00Z"),
                    ownerId = user.id!!,
                    tags = arrayOf("work"),
                ),
            )
        }

        val results =
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.searchByTitleTokens(user.id!!, "team")
            }

        assertEquals(2, results.size, "Should find 2 entries with 'team'")
        assertEquals("Meeting with team", results[0].title)
        assertEquals("Team standup", results[1].title)
    }

    @Test
    fun `searchByTitleTokens should find entries by multiple tokens`() {
        val user = testUsers.createRegularUser()

        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = Instant.parse("2024-03-15T09:00:00Z"),
                    endTime = Instant.parse("2024-03-15T10:00:00Z"),
                    ownerId = user.id!!,
                    tags = arrayOf("work"),
                ),
            )

            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Team standup",
                    startTime = Instant.parse("2024-03-15T11:00:00Z"),
                    endTime = Instant.parse("2024-03-15T11:15:00Z"),
                    ownerId = user.id!!,
                    tags = arrayOf("meeting"),
                ),
            )
        }

        val results =
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.searchByTitleTokens(user.id!!, "meeting team")
            }

        assertEquals(1, results.size, "Should find 1 entry with both 'meeting' and 'team'")
        assertEquals("Meeting with team", results[0].title)
    }

    @Test
    fun `searchByTitleTokens should deduplicate by title and return latest`() {
        val user = testUsers.createRegularUser()

        testDatabaseSupport.inTransaction {
            // Older entry with same title
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = Instant.parse("2024-03-14T09:00:00Z"),
                    endTime = Instant.parse("2024-03-14T10:00:00Z"),
                    ownerId = user.id!!,
                    tags = arrayOf("work"),
                ),
            )

            // Newer entry with same title
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = Instant.parse("2024-03-15T09:00:00Z"),
                    endTime = Instant.parse("2024-03-15T10:00:00Z"),
                    ownerId = user.id!!,
                    tags = arrayOf("work", "meeting"),
                ),
            )
        }

        val results =
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.searchByTitleTokens(user.id!!, "meeting team")
            }

        assertEquals(1, results.size, "Should deduplicate and return only 1 entry")
        assertEquals("Meeting with team", results[0].title)
        assertEquals(Instant.parse("2024-03-15T09:00:00Z"), results[0].startTime, "Should return the latest entry")
        assertEquals(2, results[0].tags.size, "Should have tags from latest entry")
    }

    @Test
    fun `searchByTitleTokens should be case insensitive`() {
        val user = testUsers.createRegularUser()

        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = Instant.parse("2024-03-15T09:00:00Z"),
                    endTime = Instant.parse("2024-03-15T10:00:00Z"),
                    ownerId = user.id!!,
                    tags = arrayOf("work"),
                ),
            )
        }

        val results =
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.searchByTitleTokens(user.id!!, "TEAM")
            }

        assertEquals(1, results.size, "Should find entry with case-insensitive search")
        assertEquals("Meeting with team", results[0].title)
    }

    @Test
    fun `searchByTitleTokens should only return current user entries`() {
        val user1 = testUsers.createRegularUser("user1", "User 1")
        val user2 = testUsers.createRegularUser("user2", "User 2")

        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = Instant.parse("2024-03-15T09:00:00Z"),
                    endTime = Instant.parse("2024-03-15T10:00:00Z"),
                    ownerId = user1.id!!,
                    tags = arrayOf("work"),
                ),
            )

            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Other user team meeting",
                    startTime = Instant.parse("2024-03-15T11:00:00Z"),
                    endTime = Instant.parse("2024-03-15T12:00:00Z"),
                    ownerId = user2.id!!,
                    tags = arrayOf("work"),
                ),
            )
        }

        val results =
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.searchByTitleTokens(user1.id!!, "team")
            }

        assertEquals(1, results.size, "Should only find user1's entries")
        assertEquals("Meeting with team", results[0].title)
    }

    @Test
    fun `searchByTitleTokens should return empty list for empty query`() {
        val user = testUsers.createRegularUser()

        val results =
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.searchByTitleTokens(user.id!!, "")
            }

        assertEquals(0, results.size, "Should return empty list for empty query")
    }
}
