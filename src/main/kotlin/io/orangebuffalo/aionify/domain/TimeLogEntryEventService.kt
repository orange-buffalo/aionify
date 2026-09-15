package io.orangebuffalo.aionify.domain

import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.transaction.annotation.TransactionalEventListener
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration

/**
 * Service for broadcasting time log entry changes to subscribers (e.g. Server-Sent Events streams).
 * Subscribers receive the changed entries and map them to their own event representation.
 */
@Singleton
open class TimeLogEntryEventService {
    private val log = LoggerFactory.getLogger(TimeLogEntryEventService::class.java)

    @Inject
    lateinit var eventPublisher: ApplicationEventPublisher<Any>

    // Changes of all users are broadcast to all subscribers, which filter them by user.
    // The sink does not buffer: changes are only delivered to active subscribers and are never replayed.
    private val eventSink = Sinks.many().multicast().directBestEffort<TimeLogEntryEventToEmit>()

    companion object {
        private const val MAX_BUFFERED_EVENTS_PER_SUBSCRIBER = 100
        private val EMIT_RETRY_DURATION: Duration = Duration.ofSeconds(1)
    }

    /**
     * Emits an event to all subscribers for the given user after the current transaction commits.
     */
    fun emitEvent(
        userId: Long,
        eventType: TimeLogEntryEventType,
        entry: TimeLogEntry,
    ) {
        eventPublisher.publishEvent(
            TimeLogEntryEventToEmit(
                userId = userId,
                eventType = eventType,
                entry = entry,
            ),
        )
    }

    @TransactionalEventListener(TransactionalEventListener.TransactionPhase.AFTER_COMMIT)
    open fun onTimeLogEntryEventToEmit(event: TimeLogEntryEventToEmit) {
        log.debug(
            "Emitting event to {} subscriber(s): {} for entry {} of user {}",
            eventSink.currentSubscriberCount(),
            event.eventType,
            event.entry.id,
            event.userId,
        )

        try {
            // Listeners can be invoked concurrently, which the sink does not allow, so emission is retried briefly
            eventSink.emitNext(event, Sinks.EmitFailureHandler.busyLooping(EMIT_RETRY_DURATION))
        } catch (e: Exception) {
            log.error("Failed to emit event for user {}", event.userId, e)
        }
    }

    /**
     * Returns changes of the given user's entries that happen while subscribed.
     * If a subscriber cannot keep up, its stream fails instead of silently losing changes,
     * so that the client reconnects and reloads the state.
     */
    fun getEventFlux(userId: Long): Flux<TimeLogEntryEventToEmit> =
        eventSink
            .asFlux()
            .filter { it.userId == userId }
            .onBackpressureBuffer(MAX_BUFFERED_EVENTS_PER_SUBSCRIBER)
}

/**
 * Event types for time log entry changes.
 */
enum class TimeLogEntryEventType {
    ENTRY_STARTED,
    ENTRY_STOPPED,
    ENTRY_UPDATED,
    ENTRY_DELETED,
}

/**
 * Event data sent via the web UI SSE stream when time log entries change.
 */
@Serdeable
data class TimeLogEntryEvent(
    val type: TimeLogEntryEventType,
    val entryId: Long,
    val title: String,
)

/**
 * Event to be emitted after a time log entry is created, updated or deleted.
 * This is used to trigger the sending of SSE events after the transaction commits.
 */
data class TimeLogEntryEventToEmit(
    val userId: Long,
    val eventType: TimeLogEntryEventType,
    val entry: TimeLogEntry,
)
