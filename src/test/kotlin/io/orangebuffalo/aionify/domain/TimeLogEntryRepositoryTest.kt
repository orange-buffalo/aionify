package io.orangebuffalo.aionify.domain

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.TestUsers
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@MicronautTest(transactional = false)
class TimeLogEntryRepositoryTest {
    @Inject
    lateinit var repository: TimeLogEntryRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    @Inject
    lateinit var testUsers: TestUsers

    private lateinit var testUser: User

    @BeforeEach
    fun cleanupDatabase() {
        testDatabaseSupport.truncateAllTables()
        testUser = testUsers.createRegularUser()
    }

    @Test
    fun `should insert and list time entries`() {
        val startTime1 = Instant.parse("2024-01-15T10:00:00Z")
        val startTime2 = Instant.parse("2024-01-15T14:30:00Z")
        val userId = requireNotNull(testUser.id)

        val entry1 =
            repository.save(
                TimeLogEntry(
                    startTime = startTime1,
                    endTime = Instant.parse("2024-01-15T11:00:00Z"),
                    title = "Task 1",
                    ownerId = userId,
                ),
            )
        val entry2 =
            repository.save(
                TimeLogEntry(
                    startTime = startTime2,
                    endTime = null,
                    title = "Task 2",
                    ownerId = userId,
                ),
            )

        assertNotNull(entry1.id)
        assertNotNull(entry2.id)
        assertEquals(startTime1, entry1.startTime)
        assertEquals(startTime2, entry2.startTime)

        val allEntries = repository.findAllOrderById()
        assertEquals(2, allEntries.size)
    }

    @Test
    fun `should find entries by owner and time range`() {
        val userId = requireNotNull(testUser.id)
        val startTime1 = Instant.parse("2024-01-15T10:00:00Z")
        val startTime2 = Instant.parse("2024-01-16T14:30:00Z")
        val startTime3 = Instant.parse("2024-01-17T09:00:00Z")

        repository.save(
            TimeLogEntry(
                startTime = startTime1,
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task 1",
                ownerId = userId,
            ),
        )
        repository.save(
            TimeLogEntry(
                startTime = startTime2,
                endTime = Instant.parse("2024-01-16T15:30:00Z"),
                title = "Task 2",
                ownerId = userId,
            ),
        )
        repository.save(
            TimeLogEntry(
                startTime = startTime3,
                endTime = null,
                title = "Task 3",
                ownerId = userId,
            ),
        )

        val fromTime = Instant.parse("2024-01-15T00:00:00Z")
        val toTime = Instant.parse("2024-01-17T00:00:00Z")

        val entries =
            repository.findByOwnerIdAndStartTimeGreaterThanEqualsAndStartTimeLessThanOrderByStartTimeDesc(
                userId,
                fromTime,
                toTime,
            )

        assertEquals(2, entries.size)
        assertEquals("Task 2", entries[0].title)
        assertEquals("Task 1", entries[1].title)
    }

    @Test
    fun `should find active entry by owner`() {
        val userId = requireNotNull(testUser.id)

        repository.save(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Completed Task",
                ownerId = userId,
            ),
        )
        val activeEntry =
            repository.save(
                TimeLogEntry(
                    startTime = Instant.parse("2024-01-15T14:30:00Z"),
                    endTime = null,
                    title = "Active Task",
                    ownerId = userId,
                ),
            )

        val found = repository.findByOwnerIdAndEndTimeIsNull(userId)
        assertTrue(found.isPresent)
        assertEquals(activeEntry.id, found.get().id)
        assertEquals("Active Task", found.get().title)
        assertNull(found.get().endTime)
    }

    @Test
    fun `should find entry by id and owner`() {
        val userId = requireNotNull(testUser.id)

        val entry =
            repository.save(
                TimeLogEntry(
                    startTime = Instant.parse("2024-01-15T10:00:00Z"),
                    endTime = null,
                    title = "My Task",
                    ownerId = userId,
                ),
            )

        val found = repository.findByIdAndOwnerId(requireNotNull(entry.id), userId)
        assertTrue(found.isPresent)
        assertEquals(entry.id, found.get().id)

        // Should not find entry for different owner
        val notFound = repository.findByIdAndOwnerId(requireNotNull(entry.id), userId + 1)
        assertTrue(notFound.isEmpty)
    }
}
