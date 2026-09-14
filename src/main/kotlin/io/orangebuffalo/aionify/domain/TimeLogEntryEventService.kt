package io.orangebuffalo.aionify.domain

import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.transaction.annotation.TransactionalEventListener
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for broadcasting time log entry changes to subscribers (e.g. Server-Sent Events streams).
 * Subscribers receive the changed entries and map them to their own event representation.
 */
@Singleton
open class TimeLogEntryEventService {
    private val log = LoggerFactory.getLogger(TimeLogEntryEventService::class.java)

    @Inject
    lateinit var eventPublisher: ApplicationEventPublisher<Any>

    // Map of userId to Sink for broadcasting events to that user's subscribers
    private val userEventSinks = ConcurrentHashMap<Long, Sinks.Many<TimeLogEntryEventToEmit>>()

    /**
     * Emits an event to all subscribers for the given user after the current transaction commits.
     * If no sink exists for the user, a warning is logged and the event is dropped.
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
        val sink = userEventSinks[event.userId]

        if (sink == null) {
            log.debug("No subscribers for user {}, event dropped: {}", event.userId, event.eventType)
            return
        }

        log.debug("Emitting event to user {}: {} for entry {}", event.userId, event.eventType, event.entry.id)

        try {
            sink.tryEmitNext(event)
        } catch (e: Exception) {
            log.error("Failed to emit event for user {}", event.userId, e)
        }
    }

    /**
     * Gets or creates a Sink for the given user.
     * Returns the sink's asFlux() for subscription.
     */
    fun getEventFlux(userId: Long): Flux<TimeLogEntryEventToEmit> {
        val sink =
            userEventSinks.computeIfAbsent(userId) {
                log.debug("Creating new event sink for user {}", userId)
                // Limit buffer size to 100 events to prevent memory issues
                Sinks.many().multicast().onBackpressureBuffer<TimeLogEntryEventToEmit>(100, false)
            }

        return sink.asFlux()
    }

    /**
     * Cleans up resources for a user when they disconnect.
     * Note: This is called when all subscribers for a user disconnect.
     */
    fun cleanupUser(userId: Long) {
        log.debug("Cleaning up event sink for user {}", userId)
        userEventSinks.remove(userId)
    }
}

/**
 * Event types for time log entry changes.
 */
enum class TimeLogEntryEventType {
    ENTRY_STARTED,
    ENTRY_STOPPED,
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
 * Event to be emitted after a time log entry is created or updated.
 * This is used to trigger the sending of SSE events after the transaction commits.
 */
data class TimeLogEntryEventToEmit(
    val userId: Long,
    val eventType: TimeLogEntryEventType,
    val entry: TimeLogEntry,
)
