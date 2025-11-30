package org.aionify.domain

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant

@QuarkusTest
class TimeEntryRepositoryTest {

    @Inject
    lateinit var repository: TimeEntryRepository

    @Test
    fun `should insert and list time entries`() {
        val startTime1 = Instant.parse("2024-01-15T10:00:00Z")
        val startTime2 = Instant.parse("2024-01-15T14:30:00Z")

        val entry1 = repository.insert(startTime1)
        val entry2 = repository.insert(startTime2)

        assertNotNull(entry1.id)
        assertNotNull(entry2.id)
        assertEquals(startTime1, entry1.startTime)
        assertEquals(startTime2, entry2.startTime)

        val allEntries = repository.findAll()
        assertEquals(2, allEntries.size)
        assertEquals(entry1.id, allEntries[0].id)
        assertEquals(startTime1, allEntries[0].startTime)
        assertEquals(entry2.id, allEntries[1].id)
        assertEquals(startTime2, allEntries[1].startTime)
    }
}
