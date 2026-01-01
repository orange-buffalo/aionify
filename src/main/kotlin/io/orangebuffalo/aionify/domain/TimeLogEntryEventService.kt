package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.sse.Event
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing Server-Sent Events (SSE) related to time log entry changes.
 * Allows broadcasting events to subscribed clients when entries are started or stopped.
 */
@Singleton
class TimeLogEntryEventService {
    private val log = LoggerFactory.getLogger(TimeLogEntryEventService::class.java)

    // Map of userId to Sink for broadcasting events to that user's subscribers
    private val userEventSinks = ConcurrentHashMap<Long, Sinks.Many<Event<TimeLogEntryEvent>>>()

    /**
     * Emits an event to all subscribers for the given user.
     * If no sink exists for the user, a warning is logged and the event is dropped.
     */
    fun emitEvent(
        userId: Long,
        eventType: TimeLogEntryEventType,
        entry: TimeLogEntry? = null,
    ) {
        val sink = userEventSinks[userId]

        if (sink == null) {
            log.debug("No subscribers for user {}, event dropped: {}", userId, eventType)
            return
        }

        val event =
            TimeLogEntryEvent(
                type = eventType,
                entryId = entry?.id,
                title = entry?.title,
            )

        log.debug("Emitting event to user {}: {}", userId, event)

        try {
            val sseEvent = Event.of(event)
            sink.tryEmitNext(sseEvent)
        } catch (e: Exception) {
            log.error("Failed to emit event for user {}", userId, e)
        }
    }

    /**
     * Gets or creates a Sink for the given user.
     * Returns the sink's asFlux() for subscription.
     */
    fun getEventFlux(userId: Long): reactor.core.publisher.Flux<Event<TimeLogEntryEvent>> {
        val sink =
            userEventSinks.computeIfAbsent(userId) {
                log.debug("Creating new event sink for user {}", userId)
                // Limit buffer size to 100 events to prevent memory issues
                Sinks.many().multicast().onBackpressureBuffer<Event<TimeLogEntryEvent>>(100, false)
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
 * Event data sent via SSE when time log entries change.
 */
@Serdeable
data class TimeLogEntryEvent(
    val type: TimeLogEntryEventType,
    val entryId: Long?,
    val title: String?,
)
