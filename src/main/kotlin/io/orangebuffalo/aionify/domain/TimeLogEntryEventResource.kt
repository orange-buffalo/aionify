package io.orangebuffalo.aionify.domain

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.sse.Event
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux

/**
 * Server-Sent Events (SSE) endpoint for real-time time log entry updates.
 * Allows authenticated users to subscribe to updates for their time log entries.
 *
 * Note: Since EventSource doesn't support custom headers, authentication is handled
 * via a custom filter that extracts the JWT token from the query parameter.
 */
@Controller("/api-ui/time-log-entries")
@Secured(SecurityRule.IS_AUTHENTICATED)
class TimeLogEntryEventResource(
    private val eventService: TimeLogEntryEventService,
) {
    private val log = LoggerFactory.getLogger(TimeLogEntryEventResource::class.java)

    /**
     * SSE endpoint that streams time log entry events to the authenticated user.
     * Keeps connection alive with periodic heartbeat events.
     */
    @Get(uri = "/events", produces = [MediaType.TEXT_EVENT_STREAM])
    fun streamEvents(currentUser: UserWithId): Flux<Event<*>> {
        val userId = currentUser.id
        log.debug("User {} subscribing to time log entry events", userId)

        val eventFlux =
            eventService
                .getEventFlux(userId)
                .doOnCancel {
                    log.debug("User {} unsubscribed from events", userId)
                    // Note: We don't cleanup here as user might have multiple tabs open
                }.doOnError { error ->
                    log.error("Error in event stream for user {}", userId, error)
                }

        // Send heartbeat events every 30 seconds to keep connection alive
        val heartbeatFlux =
            Flux
                .interval(java.time.Duration.ofSeconds(30))
                .map { Event.of<String>("heartbeat").name("heartbeat") }

        // Merge event stream with heartbeat
        return Flux.merge(eventFlux, heartbeatFlux)
    }
}

/**
 * HTTP filter that extracts JWT token from query parameter for SSE endpoints.
 * This is necessary because EventSource API doesn't support custom headers.
 *
 * Security Note: Passing JWT in URL query parameter exposes it in server logs
 * and browser history. This is a known limitation of the EventSource API.
 * The token is only used for the SSE endpoint and has the same expiration
 * as regular JWT tokens. Consider using short-lived tokens for SSE connections.
 */
@Filter("/api-ui/time-log-entries/events")
@Singleton
class SseTokenFilter : HttpServerFilter {
    private val log = LoggerFactory.getLogger(SseTokenFilter::class.java)

    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain,
    ): Publisher<MutableHttpResponse<*>> {
        // Check if token is provided in query parameter
        val tokenParam = request.parameters.get("token", String::class.java)

        return if (tokenParam.isPresent) {
            // Add token to Authorization header so Micronaut Security can process it
            val modifiedRequest =
                request
                    .mutate()
                    .header("Authorization", "Bearer ${tokenParam.get()}")

            log.trace("Added Authorization header from query parameter for SSE request")
            chain.proceed(modifiedRequest)
        } else {
            log.debug("No token found in query parameter for SSE request")
            chain.proceed(request)
        }
    }
}
