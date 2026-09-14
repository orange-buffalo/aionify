package io.orangebuffalo.aionify.api

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.sse.SseClient
import io.micronaut.http.sse.Event
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.TestUsers
import io.orangebuffalo.aionify.domain.TimeService
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import jakarta.inject.Inject
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.Disposable
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests for the public API event stream of time log entry changes.
 */
@MicronautTest(transactional = false)
class TimeLogEntryEventsApiResourceTest {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    @field:Client("/")
    lateinit var sseClient: SseClient

    @Inject
    lateinit var jsonMapper: JsonMapper

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    @Inject
    lateinit var testUsers: TestUsers

    @Inject
    lateinit var timeService: TimeService

    @Inject
    lateinit var apiRateLimitingService: ApiRateLimitingService

    private lateinit var user1: User
    private lateinit var user2: User
    private val subscriptions = mutableListOf<Disposable>()

    companion object {
        private const val EVENTS_URL = "/api/time-log-entries/events"
        private const val USER1_TOKEN = "eventsApiTokenUser1"
        private const val USER2_TOKEN = "eventsApiTokenUser2"
        private val AWAIT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()
        apiRateLimitingService.clearAllAttempts()

        user1 = testUsers.createRegularUser("eventsUser1", "Events User 1")
        user2 = testUsers.createRegularUser("eventsUser2", "Events User 2")
        insertToken(user1, USER1_TOKEN)
        insertToken(user2, USER2_TOKEN)
    }

    @AfterEach
    fun closeSubscriptions() {
        subscriptions.forEach { it.dispose() }
        subscriptions.clear()
    }

    @Test
    fun `should reject event stream without token`() {
        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(HttpRequest.GET<Any>(EVENTS_URL), String::class.java)
            }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should reject event stream with invalid token`() {
        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(HttpRequest.GET<Any>(EVENTS_URL).bearerAuth("invalid-token"), String::class.java)
            }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should stream started and stopped entries of the token owner`() {
        val events = subscribe(USER1_TOKEN)

        client.toBlocking().exchange(
            HttpRequest
                .POST(
                    "/api/time-log-entries/start",
                    StartTimeLogEntryRequest(title = "Streamed Task", metadata = listOf("project:aionify")),
                ).bearerAuth(USER1_TOKEN),
            StartTimeLogEntryResponse::class.java,
        )

        await().atMost(AWAIT_TIMEOUT).until { entryEvents(events).size == 1 }
        val startedEvent = entryEvents(events)[0]
        assertEquals("ENTRY_STARTED", startedEvent["type"])
        val startedEntry = startedEvent["entry"] as Map<*, *>
        assertEquals("Streamed Task", startedEntry["title"])
        assertEquals(listOf("project:aionify"), startedEntry["metadata"])
        assertNotNull(startedEntry["startTime"])
        assertNull(startedEntry["endTime"])

        client.toBlocking().exchange(
            HttpRequest.POST("/api/time-log-entries/stop", emptyMap<String, Any>()).bearerAuth(USER1_TOKEN),
            StopTimeLogEntryResponse::class.java,
        )

        await().atMost(AWAIT_TIMEOUT).until { entryEvents(events).size == 2 }
        val stoppedEvent = entryEvents(events)[1]
        assertEquals("ENTRY_STOPPED", stoppedEvent["type"])
        val stoppedEntry = stoppedEvent["entry"] as Map<*, *>
        assertEquals("Streamed Task", stoppedEntry["title"])
        assertNotNull(stoppedEntry["endTime"])
    }

    @Test
    fun `should not stream entries of other users`() {
        val user1Events = subscribe(USER1_TOKEN)
        val user2Events = subscribe(USER2_TOKEN)

        client.toBlocking().exchange(
            HttpRequest
                .POST("/api/time-log-entries/start", StartTimeLogEntryRequest(title = "User 1 Task"))
                .bearerAuth(USER1_TOKEN),
            StartTimeLogEntryResponse::class.java,
        )

        // Events are dispatched to all subscribers at once, so user 1 receiving the event means user 2 would have too
        await().atMost(AWAIT_TIMEOUT).until { entryEvents(user1Events).size == 1 }
        assertEquals(emptyList<Map<*, *>>(), entryEvents(user2Events))
    }

    private fun insertToken(
        user: User,
        token: String,
    ) {
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = requireNotNull(user.id),
                token = token,
                name = "Events Test",
                createdAt = timeService.now(),
            ),
        )
    }

    /**
     * Subscribes to the event stream and waits for the initial heartbeat, which confirms the subscription is active.
     */
    private fun subscribe(token: String): List<Event<String>> {
        val events = CopyOnWriteArrayList<Event<String>>()
        subscriptions +=
            Flux
                .from(sseClient.eventStream(HttpRequest.GET<Any>(EVENTS_URL).bearerAuth(token), String::class.java))
                .subscribe { events.add(it) }

        await().atMost(AWAIT_TIMEOUT).until { events.any { it.name == "heartbeat" } }
        return events
    }

    private fun entryEvents(events: List<Event<String>>): List<Map<*, *>> =
        events
            .filter { it.name == null }
            .map { requireNotNull(jsonMapper.readValue(it.data, Map::class.java)) }
}
