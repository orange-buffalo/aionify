package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.TimeLogEntryRepository
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserSettings
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@MicronautTest(transactional = false)
class TogglImportValidationPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var timeLogEntryRepository: TimeLogEntryRepository

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        regularUser = testUsers.createRegularUser("importTestUser", "Import Test User")
        testDatabaseSupport.insert(UserSettings.create(userId = requireNotNull(regularUser.id)))
    }

    private fun navigateToSettingsViaToken() {
        loginViaToken("/portal/settings", regularUser, testAuthSupport)
    }

    private fun createValidTogglCsv(entries: List<TogglTestEntry>): String {
        val header = """"Description","Tags","Start date","Start time","Stop date","Stop time""""
        val rows =
            entries.map { entry ->
                val tags = entry.tags.joinToString(", ")
                """"${entry.description}","$tags","${entry.startDate}","${entry.startTime}","${entry.endDate}","${entry.endTime}""""
            }
        return (listOf(header) + rows).joinToString("\n")
    }

    @Test
    fun `should successfully import valid Toggl CSV`() {
        navigateToSettingsViaToken()

        // Select Toggl Timer source
        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        // Create test CSV with valid data
        val csvContent =
            createValidTogglCsv(
                listOf(
                    TogglTestEntry(
                        description = "Test Entry 1",
                        tags = listOf("Tag1", "Tag2"),
                        startDate = "2025-12-19",
                        startTime = "21:01:03",
                        endDate = "2025-12-19",
                        endTime = "22:02:12",
                    ),
                    TogglTestEntry(
                        description = "Test Entry 2",
                        tags = listOf("Tag3"),
                        startDate = "2025-12-20",
                        startTime = "10:30:00",
                        endDate = "2025-12-20",
                        endTime = "11:45:00",
                    ),
                ),
            )

        // Set file input using page.setInputFiles
        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        // Click import button
        page.locator("[data-testid='start-import-button']").click()

        // Wait for success message
        val successMessage = page.locator("[data-testid='import-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Successfully imported 2 record(s)")
        assertThat(successMessage).containsText("0 duplicate(s)")

        // Verify entries were created in database
        testDatabaseSupport.inTransaction {
            val entries = timeLogEntryRepository.findAllOrderById()
            assertEquals(2, entries.size)

            val entry1 = entries.find { it.title == "Test Entry 1" }
            assertEquals("Test Entry 1", entry1?.title)
            assertEquals(2, entry1?.tags?.size)
            assertEquals("Tag1", entry1?.tags?.get(0))
            assertEquals("Tag2", entry1?.tags?.get(1))

            val entry2 = entries.find { it.title == "Test Entry 2" }
            assertEquals("Test Entry 2", entry2?.title)
            assertEquals(1, entry2?.tags?.size)
            assertEquals("Tag3", entry2?.tags?.get(0))
        }
    }

    @Test
    fun `should detect and skip duplicate entries`() {
        // Create an existing entry with same description and start time
        val existingStartTime =
            LocalDate
                .parse("2025-12-19")
                .atTime(LocalTime.parse("21:01:03"))
                .atZone(ZoneId.of("Pacific/Auckland"))
                .toInstant()

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = existingStartTime,
                endTime = existingStartTime.plusSeconds(3669), // 1 hour 1 min 9 sec later
                title = "Duplicate Entry",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("OldTag"),
            ),
        )

        navigateToSettingsViaToken()

        // Select Toggl Timer source
        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        // Create CSV with one duplicate and one new entry
        val csvContent =
            createValidTogglCsv(
                listOf(
                    TogglTestEntry(
                        description = "Duplicate Entry",
                        tags = listOf("NewTag"),
                        startDate = "2025-12-19",
                        startTime = "21:01:03",
                        endDate = "2025-12-19",
                        endTime = "22:02:12",
                    ),
                    TogglTestEntry(
                        description = "New Entry",
                        tags = listOf("Tag1"),
                        startDate = "2025-12-20",
                        startTime = "10:30:00",
                        endDate = "2025-12-20",
                        endTime = "11:45:00",
                    ),
                ),
            )

        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        page.locator("[data-testid='start-import-button']").click()

        // Wait for success message with duplicate count
        val successMessage = page.locator("[data-testid='import-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Successfully imported 1 record(s)")
        assertThat(successMessage).containsText("1 duplicate(s)")

        // Verify only one new entry was created
        testDatabaseSupport.inTransaction {
            val entries = timeLogEntryRepository.findAllOrderById()
            assertEquals(2, entries.size) // 1 existing + 1 new

            // Verify duplicate entry was not modified
            val duplicateEntry = entries.find { it.title == "Duplicate Entry" }
            assertEquals(1, duplicateEntry?.tags?.size)
            assertEquals("OldTag", duplicateEntry?.tags?.get(0))

            // Verify new entry was created
            val newEntry = entries.find { it.title == "New Entry" }
            assertEquals("New Entry", newEntry?.title)
        }
    }

    @Test
    fun `should show error for invalid CSV format with wrong number of columns`() {
        navigateToSettingsViaToken()

        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        // Create CSV with wrong number of columns
        val csvContent =
            """"Description","Tags","Start date"
"Test Entry","Tag1","2025-12-19""""

        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        page.locator("[data-testid='start-import-button']").click()

        // Wait for error message
        val errorMessage = page.locator("[data-testid='import-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Invalid CSV format")
    }

    @Test
    fun `should show error for invalid CSV format with wrong header`() {
        navigateToSettingsViaToken()

        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        // Create CSV with wrong header
        val csvContent =
            """"Title","Labels","Begin date","Begin time","End date","End time"
"Test Entry","Tag1","2025-12-19","21:01:03","2025-12-19","22:02:12""""

        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        page.locator("[data-testid='start-import-button']").click()

        // Wait for error message
        val errorMessage = page.locator("[data-testid='import-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Invalid CSV format")
    }

    @Test
    fun `should handle empty CSV file`() {
        navigateToSettingsViaToken()

        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        val csvContent = ""

        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        page.locator("[data-testid='start-import-button']").click()

        // Wait for error message - empty body is treated as Bad Request by Micronaut
        val errorMessage = page.locator("[data-testid='import-error']")
        assertThat(errorMessage).isVisible()
        // The error message could be either "Invalid CSV format" or "An error occurred"
        // depending on how the server handles empty body
    }

    @Test
    fun `should handle CSV with only header`() {
        navigateToSettingsViaToken()

        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        val csvContent = """"Description","Tags","Start date","Start time","Stop date","Stop time""""

        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        page.locator("[data-testid='start-import-button']").click()

        // Should succeed with 0 records imported
        val successMessage = page.locator("[data-testid='import-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Successfully imported 0 record(s)")
    }

    @Test
    fun `should import entries with no tags`() {
        navigateToSettingsViaToken()

        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        val csvContent =
            createValidTogglCsv(
                listOf(
                    TogglTestEntry(
                        description = "Entry without tags",
                        tags = emptyList(),
                        startDate = "2025-12-19",
                        startTime = "21:01:03",
                        endDate = "2025-12-19",
                        endTime = "22:02:12",
                    ),
                ),
            )

        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        page.locator("[data-testid='start-import-button']").click()

        val successMessage = page.locator("[data-testid='import-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Successfully imported 1 record(s)")

        // Verify entry has no tags and correct data
        testDatabaseSupport.inTransaction {
            val entries = timeLogEntryRepository.findAllOrderById()
            assertEquals(1, entries.size)

            val entry = entries[0]
            assertEquals("Entry without tags", entry.title)
            assertEquals(0, entry.tags.size)

            // Verify timestamps are correct (in Pacific/Auckland timezone)
            val expectedStart =
                LocalDate
                    .parse("2025-12-19")
                    .atTime(LocalTime.parse("21:01:03"))
                    .atZone(ZoneId.of("Pacific/Auckland"))
                    .toInstant()
            val expectedEnd =
                LocalDate
                    .parse("2025-12-19")
                    .atTime(LocalTime.parse("22:02:12"))
                    .atZone(ZoneId.of("Pacific/Auckland"))
                    .toInstant()

            assertEquals(expectedStart, entry.startTime)
            assertEquals(expectedEnd, entry.endTime)
        }
    }

    @Test
    fun `should import entry with single tag and verify data`() {
        navigateToSettingsViaToken()

        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        val csvContent =
            createValidTogglCsv(
                listOf(
                    TogglTestEntry(
                        description = "Single tag entry",
                        tags = listOf("Development"),
                        startDate = "2025-12-20",
                        startTime = "09:00:00",
                        endDate = "2025-12-20",
                        endTime = "10:30:00",
                    ),
                ),
            )

        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        page.locator("[data-testid='start-import-button']").click()

        val successMessage = page.locator("[data-testid='import-success']")
        assertThat(successMessage).isVisible()

        // Verify entry with single tag and all fields
        testDatabaseSupport.inTransaction {
            val entries = timeLogEntryRepository.findAllOrderById()
            assertEquals(1, entries.size)

            val entry = entries[0]
            assertEquals("Single tag entry", entry.title)
            assertEquals(1, entry.tags.size)
            assertEquals("Development", entry.tags[0])
            assertEquals(requireNotNull(regularUser.id), entry.ownerId)

            // Verify the duration is correct
            val expectedStart =
                LocalDate
                    .parse("2025-12-20")
                    .atTime(LocalTime.parse("09:00:00"))
                    .atZone(ZoneId.of("Pacific/Auckland"))
                    .toInstant()
            val expectedEnd =
                LocalDate
                    .parse("2025-12-20")
                    .atTime(LocalTime.parse("10:30:00"))
                    .atZone(ZoneId.of("Pacific/Auckland"))
                    .toInstant()

            assertEquals(expectedStart, entry.startTime)
            assertEquals(expectedEnd, entry.endTime)
        }
    }

    @Test
    fun `should import entry with multiple tags and verify all data`() {
        navigateToSettingsViaToken()

        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        val csvContent =
            createValidTogglCsv(
                listOf(
                    TogglTestEntry(
                        description = "Multi-tag entry",
                        tags = listOf("Frontend", "React", "UI"),
                        startDate = "2025-12-21",
                        startTime = "14:15:00",
                        endDate = "2025-12-21",
                        endTime = "16:45:30",
                    ),
                ),
            )

        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        page.locator("[data-testid='start-import-button']").click()

        val successMessage = page.locator("[data-testid='import-success']")
        assertThat(successMessage).isVisible()

        // Verify entry with multiple tags preserves order and all data
        testDatabaseSupport.inTransaction {
            val entries = timeLogEntryRepository.findAllOrderById()
            assertEquals(1, entries.size)

            val entry = entries[0]
            assertEquals("Multi-tag entry", entry.title)
            assertEquals(3, entry.tags.size)
            assertEquals("Frontend", entry.tags[0])
            assertEquals("React", entry.tags[1])
            assertEquals("UI", entry.tags[2])

            // Verify timestamps
            val expectedStart =
                LocalDate
                    .parse("2025-12-21")
                    .atTime(LocalTime.parse("14:15:00"))
                    .atZone(ZoneId.of("Pacific/Auckland"))
                    .toInstant()
            val expectedEnd =
                LocalDate
                    .parse("2025-12-21")
                    .atTime(LocalTime.parse("16:45:30"))
                    .atZone(ZoneId.of("Pacific/Auckland"))
                    .toInstant()

            assertEquals(expectedStart, entry.startTime)
            assertEquals(expectedEnd, entry.endTime)
            assertEquals(requireNotNull(regularUser.id), entry.ownerId)
        }
    }

    @Test
    fun `should import entry spanning across days`() {
        navigateToSettingsViaToken()

        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        val csvContent =
            createValidTogglCsv(
                listOf(
                    TogglTestEntry(
                        description = "Late night work",
                        tags = listOf("Overtime"),
                        startDate = "2025-12-22",
                        startTime = "23:30:00",
                        endDate = "2025-12-23",
                        endTime = "01:15:00",
                    ),
                ),
            )

        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        page.locator("[data-testid='start-import-button']").click()

        val successMessage = page.locator("[data-testid='import-success']")
        assertThat(successMessage).isVisible()

        // Verify entry spanning multiple days
        testDatabaseSupport.inTransaction {
            val entries = timeLogEntryRepository.findAllOrderById()
            assertEquals(1, entries.size)

            val entry = entries[0]
            assertEquals("Late night work", entry.title)

            val expectedStart =
                LocalDate
                    .parse("2025-12-22")
                    .atTime(LocalTime.parse("23:30:00"))
                    .atZone(ZoneId.of("Pacific/Auckland"))
                    .toInstant()
            val expectedEnd =
                LocalDate
                    .parse("2025-12-23")
                    .atTime(LocalTime.parse("01:15:00"))
                    .atZone(ZoneId.of("Pacific/Auckland"))
                    .toInstant()

            assertEquals(expectedStart, entry.startTime)
            assertEquals(expectedEnd, entry.endTime)
            // Verify end time is after start time
            assert(requireNotNull(entry.endTime).isAfter(requireNotNull(entry.startTime)))
        }
    }

    @Test
    fun `should verify exact timestamps and user ownership for imported entries`() {
        navigateToSettingsViaToken()

        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        val csvContent =
            createValidTogglCsv(
                listOf(
                    TogglTestEntry(
                        description = "Precise timing test",
                        tags = listOf("Testing"),
                        startDate = "2025-12-19",
                        startTime = "13:07:42",
                        endDate = "2025-12-19",
                        endTime = "15:22:18",
                    ),
                ),
            )

        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        page.locator("[data-testid='start-import-button']").click()

        val successMessage = page.locator("[data-testid='import-success']")
        assertThat(successMessage).isVisible()

        // Verify precise timestamps and ownership
        testDatabaseSupport.inTransaction {
            val entries = timeLogEntryRepository.findAllOrderById()
            assertEquals(1, entries.size)

            val entry = entries[0]
            assertEquals("Precise timing test", entry.title)
            assertEquals(requireNotNull(regularUser.id), entry.ownerId)

            // Verify exact timestamps including seconds
            val expectedStart =
                LocalDate
                    .parse("2025-12-19")
                    .atTime(LocalTime.parse("13:07:42"))
                    .atZone(ZoneId.of("Pacific/Auckland"))
                    .toInstant()
            val expectedEnd =
                LocalDate
                    .parse("2025-12-19")
                    .atTime(LocalTime.parse("15:22:18"))
                    .atZone(ZoneId.of("Pacific/Auckland"))
                    .toInstant()

            assertEquals(expectedStart, entry.startTime)
            assertEquals(expectedEnd, entry.endTime)

            // Verify duration calculation: 2 hours (7200s) + 14 minutes (840s) + 36 seconds = 8076s
            val durationSeconds =
                java.time.Duration
                    .between(entry.startTime, entry.endTime)
                    .seconds
            val expectedDuration = (2 * 3600) + (14 * 60) + 36 // 2h 14m 36s
            assertEquals(expectedDuration.toLong(), durationSeconds)
        }
    }

    @Test
    fun `should handle multiple entries with one duplicate and two new`() {
        // Create an existing entry
        val existingStartTime =
            LocalDate
                .parse("2025-12-19")
                .atTime(LocalTime.parse("21:01:03"))
                .atZone(ZoneId.of("Pacific/Auckland"))
                .toInstant()

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = existingStartTime,
                endTime = existingStartTime.plusSeconds(3669), // 1 hour 1 min 9 sec later
                title = "Existing Entry",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("OldTag"),
            ),
        )

        navigateToSettingsViaToken()

        // Select Toggl Timer source
        page.locator("[data-testid='import-source-select']").click()
        page.locator("[data-testid='import-source-toggl']").click()

        // Create CSV with one duplicate and two new entries
        val csvContent =
            createValidTogglCsv(
                listOf(
                    TogglTestEntry(
                        description = "Existing Entry",
                        tags = listOf("NewTag"),
                        startDate = "2025-12-19",
                        startTime = "21:01:03",
                        endDate = "2025-12-19",
                        endTime = "22:02:12",
                    ),
                    TogglTestEntry(
                        description = "New Entry 1",
                        tags = listOf("Tag1"),
                        startDate = "2025-12-20",
                        startTime = "10:30:00",
                        endDate = "2025-12-20",
                        endTime = "11:45:00",
                    ),
                    TogglTestEntry(
                        description = "New Entry 2",
                        tags = listOf("Tag2", "Tag3"),
                        startDate = "2025-12-21",
                        startTime = "14:00:00",
                        endDate = "2025-12-21",
                        endTime = "15:30:00",
                    ),
                ),
            )

        page.locator("[data-testid='import-file-input']").setInputFiles(
            com.microsoft.playwright.options.FilePayload(
                "test.csv",
                "text/csv",
                csvContent.toByteArray(),
            ),
        )

        page.locator("[data-testid='start-import-button']").click()

        // Wait for success message with import and duplicate counts
        val successMessage = page.locator("[data-testid='import-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Successfully imported 2 record(s)")
        assertThat(successMessage).containsText("1 duplicate(s)")

        // Verify database state
        testDatabaseSupport.inTransaction {
            val entries = timeLogEntryRepository.findAllOrderById()
            assertEquals(3, entries.size) // 1 existing + 2 new

            // Verify existing entry was not modified
            val existingEntry = entries.find { it.title == "Existing Entry" }
            assertEquals("Existing Entry", existingEntry?.title)
            assertEquals(1, existingEntry?.tags?.size)
            assertEquals("OldTag", existingEntry?.tags?.get(0))

            // Verify first new entry was created
            val newEntry1 = entries.find { it.title == "New Entry 1" }
            assertEquals("New Entry 1", newEntry1?.title)
            assertEquals(1, newEntry1?.tags?.size)
            assertEquals("Tag1", newEntry1?.tags?.get(0))

            // Verify second new entry was created
            val newEntry2 = entries.find { it.title == "New Entry 2" }
            assertEquals("New Entry 2", newEntry2?.title)
            assertEquals(2, newEntry2?.tags?.size)
            assertEquals("Tag2", newEntry2?.tags?.get(0))
            assertEquals("Tag3", newEntry2?.tags?.get(1))
        }
    }
}

data class TogglTestEntry(
    val description: String,
    val tags: List<String>,
    val startDate: String,
    val startTime: String,
    val endDate: String,
    val endTime: String,
)
