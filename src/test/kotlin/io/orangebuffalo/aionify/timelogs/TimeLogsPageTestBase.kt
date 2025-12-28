package io.orangebuffalo.aionify.timelogs

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.PlaywrightTestBase
import io.orangebuffalo.aionify.TestAuthSupport
import io.orangebuffalo.aionify.TimeLogsPageObject
import io.orangebuffalo.aionify.domain.TimeLogEntryRepository
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach

/**
 * Base class for Time Logs page Playwright tests.
 * Provides common setup and utilities for all time logs page tests.
 */
@MicronautTest(transactional = false)
abstract class TimeLogsPageTestBase : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var timeLogEntryRepository: TimeLogEntryRepository

    protected lateinit var testUser: User
    protected lateinit var timeLogsPage: TimeLogsPageObject

    @BeforeEach
    fun setupTimeLogsTest() {
        testUser = testUsers.createRegularUser()
        timeLogsPage = TimeLogsPageObject(page)
    }
}
