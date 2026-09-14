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
import io.orangebuffalo.aionify.TestAuthSupport
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.TestUsers
import io.orangebuffalo.aionify.domain.TimeLogEntryRepository
import io.orangebuffalo.aionify.domain.TimeService
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import jakarta.inject.Inject
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.Disposable
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

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
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var timeService: TimeService

    @Inject
    lateinit var timeLogEntryRepository: TimeLogEntryRepository

    @Inject
    lateinit var apiRateLimitingService: ApiRateLimitingService

    private lateinit var user1: User
    private lateinit var user2: User
    private lateinit var user1Token: UserApiAccessToken
    private val subscriptions = mutableListOf<EventStreamSubscription>()

    companion object {
        private const val EVENTS_URL = "/api/time-log-entries/events"
        private const val USER1_TOKEN = "eventsApiTokenUser1"
        private const val USER2_TOKEN = "eventsApiTokenUser2"
        private val AWAIT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }

    private class EventStreamSubscription(
        val events: MutableList<Event<String>>,
        val terminated: AtomicBoolean,
    ) {
        lateinit var disposable: Disposable
    }

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()
        apiRateLimitingService.clearAllAttempts()

        user1 = testUsers.createRegularUser("eventsUser1", "Events User 1")
        user2 = testUsers.createRegularUser("eventsUser2", "Events User 2")
        user1Token = insertToken(user1, USER1_TOKEN, "Events Test")
        insertToken(user2, USER2_TOKEN, "Events Test")
    }

    @AfterEach
    fun closeSubscriptions() {
        subscriptions.forEach { it.disposable.dispose() }
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
        assertStreamRejected("invalid-token")
    }

    @Test
    fun `should stream started and stopped entries of the token owner`() {
        val subscription = subscribe(USER1_TOKEN)

        client.toBlocking().exchange(
            HttpRequest
                .POST(
                    "/api/time-log-entries/start",
                    StartTimeLogEntryRequest(title = "Streamed Task", metadata = listOf("project:aionify")),
                ).bearerAuth(USER1_TOKEN),
            StartTimeLogEntryResponse::class.java,
        )

        await().atMost(AWAIT_TIMEOUT).until { entryEvents(subscription).size == 1 }
        val startedEvent = entryEvents(subscription)[0]
        assertEquals("ENTRY_STARTED", startedEvent["type"])
        val startedEntry = startedEvent["entry"] as Map<*, *>
        assertEquals("Streamed Task", startedEntry["title"])
        assertEquals(listOf("project:aionify"), startedEntry["metadata"])
        assertNotNull(startedEntry["startTime"])
        assertNull(startedEntry["endTime"])

        stopEntryViaApi(USER1_TOKEN)

        await().atMost(AWAIT_TIMEOUT).until { entryEvents(subscription).size == 2 }
        val stoppedEvent = entryEvents(subscription)[1]
        assertEquals("ENTRY_STOPPED", stoppedEvent["type"])
        val stoppedEntry = stoppedEvent["entry"] as Map<*, *>
        assertEquals("Streamed Task", stoppedEntry["title"])
        assertNotNull(stoppedEntry["endTime"])
    }

    @Test
    fun `should stream entries stopped via the web UI`() {
        val subscription = subscribe(USER1_TOKEN)
        startEntryViaApi(USER1_TOKEN, "Stopped In Web UI")
        await().atMost(AWAIT_TIMEOUT).until { entryEvents(subscription).size == 1 }

        val activeEntryId =
            testDatabaseSupport.inTransaction {
                requireNotNull(timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(user1.id)).get().id)
            }
        client.toBlocking().exchange(
            HttpRequest
                .PUT("/api-ui/time-log-entries/$activeEntryId/stop", emptyMap<String, Any>())
                .bearerAuth(testAuthSupport.generateToken(user1)),
            Map::class.java,
        )

        await().atMost(AWAIT_TIMEOUT).until { entryEvents(subscription).size == 2 }
        val stoppedEvent = entryEvents(subscription)[1]
        assertEquals("ENTRY_STOPPED", stoppedEvent["type"])
        assertEquals("Stopped In Web UI", (stoppedEvent["entry"] as Map<*, *>)["title"])
    }

    @Test
    fun `should not stream entries of other users`() {
        val user1Subscription = subscribe(USER1_TOKEN)
        val user2Subscription = subscribe(USER2_TOKEN)

        startEntryViaApi(USER1_TOKEN, "User 1 Task")

        // Events are dispatched to all subscribers at once, so user 1 receiving the event means user 2 would have too
        await().atMost(AWAIT_TIMEOUT).until { entryEvents(user1Subscription).size == 1 }
        assertEquals(emptyList<Map<*, *>>(), entryEvents(user2Subscription))
    }

    @Test
    fun `should not replay changes made while disconnected`() {
        subscribe(USER1_TOKEN).disposable.dispose()

        startEntryViaApi(USER1_TOKEN, "Missed Task")
        stopEntryViaApi(USER1_TOKEN)

        val reconnected = subscribe(USER1_TOKEN)
        startEntryViaApi(USER1_TOKEN, "Live Task")

        // Replayed changes would be delivered before the live one
        await().atMost(AWAIT_TIMEOUT).until { entryEvents(reconnected).isNotEmpty() }
        assertEquals(listOf("Live Task"), entryEvents(reconnected).map { (it["entry"] as Map<*, *>)["title"] })
    }

    @Test
    fun `should close event stream when its token is deleted`() {
        val subscription = subscribe(USER1_TOKEN)

        client.toBlocking().exchange(
            HttpRequest
                .DELETE<Any>("/api-ui/users/api-tokens/${user1Token.id}")
                .bearerAuth(testAuthSupport.generateToken(user1)),
            Map::class.java,
        )

        await().atMost(AWAIT_TIMEOUT).until { subscription.terminated.get() }
        assertStreamRejected(USER1_TOKEN)
    }

    @Test
    fun `should close event stream when its token is regenerated`() {
        val subscription = subscribe(USER1_TOKEN)

        client.toBlocking().exchange(
            HttpRequest
                .PUT("/api-ui/users/api-tokens/${user1Token.id}", emptyMap<String, Any>())
                .bearerAuth(testAuthSupport.generateToken(user1)),
            Map::class.java,
        )

        await().atMost(AWAIT_TIMEOUT).until { subscription.terminated.get() }
        assertStreamRejected(USER1_TOKEN)
    }

    @Test
    fun `should keep event stream open when another token is revoked`() {
        val otherToken = insertToken(user1, "eventsApiTokenUser1Other", "Other Integration")
        val subscription = subscribe(USER1_TOKEN)

        client.toBlocking().exchange(
            HttpRequest
                .DELETE<Any>("/api-ui/users/api-tokens/${otherToken.id}")
                .bearerAuth(testAuthSupport.generateToken(user1)),
            Map::class.java,
        )
        startEntryViaApi(USER1_TOKEN, "Still Streamed")

        await().atMost(AWAIT_TIMEOUT).until { entryEvents(subscription).size == 1 }
        assertFalse(subscription.terminated.get(), "Stream should stay open")
    }

    private fun insertToken(
        user: User,
        token: String,
        name: String,
    ): UserApiAccessToken =
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = requireNotNull(user.id),
                token = token,
                name = name,
                createdAt = timeService.now(),
            ),
        )

    private fun startEntryViaApi(
        token: String,
        title: String,
    ) {
        client.toBlocking().exchange(
            HttpRequest.POST("/api/time-log-entries/start", StartTimeLogEntryRequest(title = title)).bearerAuth(token),
            StartTimeLogEntryResponse::class.java,
        )
    }

    private fun stopEntryViaApi(token: String) {
        client.toBlocking().exchange(
            HttpRequest.POST("/api/time-log-entries/stop", emptyMap<String, Any>()).bearerAuth(token),
            StopTimeLogEntryResponse::class.java,
        )
    }

    private fun assertStreamRejected(token: String) {
        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(HttpRequest.GET<Any>(EVENTS_URL).bearerAuth(token), String::class.java)
            }
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    /**
     * Subscribes to the event stream and waits for the initial heartbeat, which confirms the subscription is active.
     */
    private fun subscribe(token: String): EventStreamSubscription {
        val subscription = EventStreamSubscription(CopyOnWriteArrayList(), AtomicBoolean(false))
        subscription.disposable =
            Flux
                .from(sseClient.eventStream(HttpRequest.GET<Any>(EVENTS_URL).bearerAuth(token), String::class.java))
                .subscribe(
                    { subscription.events.add(it) },
                    { subscription.terminated.set(true) },
                    { subscription.terminated.set(true) },
                )
        subscriptions += subscription

        await().atMost(AWAIT_TIMEOUT).until { subscription.events.any { it.name == "heartbeat" } }
        return subscription
    }

    private fun entryEvents(subscription: EventStreamSubscription): List<Map<*, *>> =
        subscription.events
            .filter { it.name == null }
            .map { requireNotNull(jsonMapper.readValue(it.data, Map::class.java)) }
}
