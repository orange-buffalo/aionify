package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.timeInTestTz
import io.orangebuffalo.aionify.withLocalTime
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for autocomplete functionality when editing time log entry titles inline.
 * Verifies that autocompletion works during editing, and that selecting an entry
 * immediately saves both title and tags.
 */
class TimeLogsEditAutocompleteTest : TimeLogsPageTestBase() {
    @Test
    fun `should show autocomplete suggestions when editing title`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create existing entries for autocomplete suggestions (outside current week view)
        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = timeInTestTz("2024-03-08", "22:00"),
                    endTime = timeInTestTz("2024-03-08", "23:00"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work", "meeting"),
                ),
            )
        }

        // Create the entry we will edit
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Original Title",
                ownerId = testUser.id!!,
                tags = emptyArray(),
            ),
        )

        page.clock().resume()
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open inline title edit
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).isVisible()

        // Type to trigger autocomplete
        val input = page.locator("[data-testid='time-entry-inline-title-input']")
        input.fill("meeting")

        // Autocomplete popover should appear
        val autocompletePopover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(autocompletePopover).isVisible()

        // Check suggestion is visible
        val suggestions = page.locator("[data-testid^='autocomplete-item-']")
        assertThat(suggestions).containsText(arrayOf("Meeting with team"))
    }

    @Test
    fun `should save title and tags with no tags when selecting autocomplete entry`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a suggestion entry with no tags (outside current week view)
        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Bug fix session",
                    startTime = timeInTestTz("2024-03-08", "20:00"),
                    endTime = timeInTestTz("2024-03-08", "21:00"),
                    ownerId = testUser.id!!,
                    tags = emptyArray(),
                ),
            )
        }

        // Create the entry we will edit
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Title",
                    ownerId = testUser.id!!,
                    tags = arrayOf("old-tag"),
                ),
            )

        page.clock().resume()
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open inline title edit
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()

        // Type to trigger autocomplete
        val input = page.locator("[data-testid='time-entry-inline-title-input']")
        input.fill("bug fix")

        // Wait for suggestions
        val autocompletePopover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(autocompletePopover).isVisible()

        // Select the suggestion
        page.locator("[data-testid='autocomplete-item-0']").click()

        // Popover should close
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).not().isVisible()

        // Verify title updated in UI
        assertThat(page.locator("[data-testid='time-entry-inline-title-trigger']")).containsText("Bug fix session")

        // Verify database state - title and tags updated
        await untilAsserted {
            testDatabaseSupport.inTransaction {
                val updatedEntry = timeLogEntryRepository.findById(entry.id!!).orElseThrow()
                assertEquals("Bug fix session", updatedEntry.title)
                assertEquals(emptySet<String>(), updatedEntry.tags.toSet())
            }
        }
    }

    @Test
    fun `should save title and tags with single tag when selecting autocomplete entry`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a suggestion entry with a single tag (outside current week view)
        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Code review",
                    startTime = timeInTestTz("2024-03-08", "20:00"),
                    endTime = timeInTestTz("2024-03-08", "21:00"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work"),
                ),
            )
        }

        // Create the entry we will edit - no tags initially
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Title",
                    ownerId = testUser.id!!,
                    tags = emptyArray(),
                ),
            )

        page.clock().resume()
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open inline title edit
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()

        val input = page.locator("[data-testid='time-entry-inline-title-input']")
        input.fill("code review")

        val autocompletePopover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(autocompletePopover).isVisible()

        // Select the suggestion
        page.locator("[data-testid='autocomplete-item-0']").click()

        // Popover should close
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).not().isVisible()

        // Verify title updated in UI
        assertThat(page.locator("[data-testid='time-entry-inline-title-trigger']")).containsText("Code review")

        // Verify database state
        await untilAsserted {
            testDatabaseSupport.inTransaction {
                val updatedEntry = timeLogEntryRepository.findById(entry.id!!).orElseThrow()
                assertEquals("Code review", updatedEntry.title)
                assertEquals(setOf("work"), updatedEntry.tags.toSet())
            }
        }
    }

    @Test
    fun `should save title and tags with multiple tags when selecting autocomplete entry`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a suggestion entry with multiple tags (outside current week view)
        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = timeInTestTz("2024-03-08", "22:00"),
                    endTime = timeInTestTz("2024-03-08", "23:00"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work", "meeting"),
                ),
            )
        }

        // Create the entry we will edit
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Title",
                    ownerId = testUser.id!!,
                    tags = arrayOf("old-tag"),
                ),
            )

        page.clock().resume()
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open inline title edit
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()

        val input = page.locator("[data-testid='time-entry-inline-title-input']")
        input.fill("meeting")

        val autocompletePopover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(autocompletePopover).isVisible()

        // Select the suggestion
        page.locator("[data-testid='autocomplete-item-0']").click()

        // Popover should close
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).not().isVisible()

        // Verify title updated in UI
        assertThat(page.locator("[data-testid='time-entry-inline-title-trigger']")).containsText("Meeting with team")

        // Verify database state - both title and tags from autocomplete are saved
        await untilAsserted {
            testDatabaseSupport.inTransaction {
                val updatedEntry = timeLogEntryRepository.findById(entry.id!!).orElseThrow()
                assertEquals("Meeting with team", updatedEntry.title)
                assertEquals(setOf("work", "meeting"), updatedEntry.tags.toSet())
            }
        }
    }

    @Test
    fun `should preserve time fields when selecting autocomplete entry`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Code review",
                    startTime = timeInTestTz("2024-03-08", "20:00"),
                    endTime = timeInTestTz("2024-03-08", "21:00"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work"),
                ),
            )
        }

        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Title",
                    ownerId = testUser.id!!,
                    tags = arrayOf("old-tag"),
                ),
            )

        page.clock().resume()
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open inline title edit and select autocomplete
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()
        page.locator("[data-testid='time-entry-inline-title-input']").fill("code review")

        assertThat(page.locator("[data-testid='autocomplete-popover']")).isVisible()
        page.locator("[data-testid='autocomplete-item-0']").click()

        // Verify database state - start and end time preserved
        await untilAsserted {
            testDatabaseSupport.inTransaction {
                val updatedEntry = timeLogEntryRepository.findById(entry.id!!).orElseThrow()
                assertEquals("Code review", updatedEntry.title)
                assertEquals(entry.startTime, updatedEntry.startTime)
                assertEquals(entry.endTime, updatedEntry.endTime)
            }
        }
    }

    @Test
    fun `should work with keyboard selection on edit autocomplete`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create suggestion entries (outside current week view)
        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Team standup",
                    startTime = timeInTestTz("2024-03-08", "00:00"),
                    endTime = timeInTestTz("2024-03-08", "00:15"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("meeting"),
                ),
            )
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = timeInTestTz("2024-03-07", "22:00"),
                    endTime = timeInTestTz("2024-03-07", "23:00"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work", "meeting"),
                ),
            )
        }

        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Title",
                    ownerId = testUser.id!!,
                    tags = emptyArray(),
                ),
            )

        page.clock().resume()
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open inline title edit
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()

        val input = page.locator("[data-testid='time-entry-inline-title-input']")
        input.fill("team")

        val autocompletePopover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(autocompletePopover).isVisible()

        // Navigate with keyboard: ArrowDown twice to select the second item
        input.press("ArrowDown")
        input.press("ArrowDown")

        val secondItem = page.locator("[data-testid='autocomplete-item-1']")
        assertThat(secondItem).hasAttribute("data-highlighted", "true")

        // Press Enter to select
        input.press("Enter")

        // Popover should close
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).not().isVisible()

        // Verify database state
        await untilAsserted {
            testDatabaseSupport.inTransaction {
                val updatedEntry = timeLogEntryRepository.findById(entry.id!!).orElseThrow()
                assertEquals("Meeting with team", updatedEntry.title)
                assertEquals(setOf("work", "meeting"), updatedEntry.tags.toSet())
            }
        }
    }

    @Test
    fun `should save title and tags on grouped entry autocomplete selection`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create autocomplete suggestion source (outside current week view)
        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Code review",
                    startTime = timeInTestTz("2024-03-08", "20:00"),
                    endTime = timeInTestTz("2024-03-08", "21:00"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work", "review"),
                ),
            )
        }

        // Create grouped entries (same title and tags)
        val entry1 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("00:30"),
                    endTime = baseTime.withLocalTime("01:00"),
                    title = "Original Group",
                    ownerId = testUser.id!!,
                    tags = arrayOf("old"),
                ),
            )
        val entry2 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("01:30"),
                    endTime = baseTime.withLocalTime("02:00"),
                    title = "Original Group",
                    ownerId = testUser.id!!,
                    tags = arrayOf("old"),
                ),
            )

        page.clock().resume()
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Find grouped entry
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        assertThat(groupedEntry).isVisible()

        // Open inline title edit on grouped entry
        groupedEntry.locator("[data-testid='grouped-entry-inline-title-trigger']").click()
        assertThat(page.locator("[data-testid='grouped-entry-inline-title-popover']")).isVisible()

        // Type to trigger autocomplete
        val input = page.locator("[data-testid='grouped-entry-inline-title-input']")
        input.fill("code review")

        val autocompletePopover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(autocompletePopover).isVisible()

        // Select the suggestion
        page.locator("[data-testid='autocomplete-item-0']").click()

        // Popover should close
        assertThat(page.locator("[data-testid='grouped-entry-inline-title-popover']")).not().isVisible()

        // Verify all grouped entries in database were updated with both title and tags
        await untilAsserted {
            testDatabaseSupport.inTransaction {
                val updated1 = timeLogEntryRepository.findById(entry1.id!!).orElseThrow()
                val updated2 = timeLogEntryRepository.findById(entry2.id!!).orElseThrow()

                assertEquals("Code review", updated1.title)
                assertEquals(setOf("work", "review"), updated1.tags.toSet())
                assertEquals(entry1.startTime, updated1.startTime)
                assertEquals(entry1.endTime, updated1.endTime)

                assertEquals("Code review", updated2.title)
                assertEquals(setOf("work", "review"), updated2.tags.toSet())
                assertEquals(entry2.startTime, updated2.startTime)
                assertEquals(entry2.endTime, updated2.endTime)
            }
        }
    }

    @Test
    fun `should still allow manual title save without autocomplete`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Title",
                    ownerId = testUser.id!!,
                    tags = arrayOf("keep-this"),
                ),
            )

        page.clock().resume()
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open inline title edit
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()

        // Change the title manually (without using autocomplete)
        val input = page.locator("[data-testid='time-entry-inline-title-input']")
        input.fill("Updated Manual Title")

        // Click save button
        page.locator("[data-testid='time-entry-inline-title-save-button']").click()

        // Popover should close
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).not().isVisible()

        // Verify title updated in UI
        assertThat(page.locator("[data-testid='time-entry-inline-title-trigger']")).containsText("Updated Manual Title")

        // Verify database state - title updated but tags preserved
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(entry.id!!).orElseThrow()
            assertEquals("Updated Manual Title", updatedEntry.title)
            assertEquals(setOf("keep-this"), updatedEntry.tags.toSet())
        }
    }
}
