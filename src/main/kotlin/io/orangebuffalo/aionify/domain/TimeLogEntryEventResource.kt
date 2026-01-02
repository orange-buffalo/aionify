package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.sse.Event
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Hidden
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux

/**
 * Server-Sent Events (SSE) endpoint for real-time time log entry updates.
 * Allows authenticated users to subscribe to updates for their time log entries.
 *
 * Note: Since EventSource doesn't support custom headers, authentication is handled
 * via short-lived tokens that are generated on-demand and passed in query parameters.
 */
@Controller("/api-ui/time-log-entries")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Hidden
class TimeLogEntryEventResource(
    private val eventService: TimeLogEntryEventService,
    private val sseTokenService: SseTokenService,
) {
    private val log = LoggerFactory.getLogger(TimeLogEntryEventResource::class.java)

    /**
     * Generates a short-lived token for SSE authentication.
     * The token expires after 30 seconds.
     */
    @Post("/sse-token")
    fun generateSseToken(currentUser: UserWithId): SseTokenResponse {
        val token = sseTokenService.generateToken(currentUser.id)
        log.debug("Generated SSE token for user {}", currentUser.id)
        return SseTokenResponse(token)
    }

    /**
     * SSE endpoint that streams time log entry events to the authenticated user.
     * Keeps connection alive with periodic heartbeat events.
     * 
     * Note: This endpoint uses custom authentication via SseTokenFilter and is marked
     * as anonymous to prevent Micronaut Security from interfering with the SSE stream.
     */
    @Get(uri = "/events", produces = [MediaType.TEXT_EVENT_STREAM])
    @Secured(SecurityRule.IS_ANONYMOUS)
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
 * Response containing a short-lived SSE token.
 */
@Serdeable
@Introspected
data class SseTokenResponse(
    val token: String,
)

/**
 * HTTP filter that validates short-lived SSE tokens from query parameters.
 * This is necessary because EventSource API doesn't support custom headers.
 *
 * Security Note: The short-lived tokens (30 second TTL) reduce security risks
 * compared to passing long-lived JWT tokens in URL query parameters.
 */
@Filter("/api-ui/time-log-entries/events")
@Singleton
class SseTokenFilter(
    private val sseTokenService: SseTokenService,
    private val userRepository: UserRepository,
) : HttpServerFilter, io.micronaut.core.order.Ordered {
    private val log = LoggerFactory.getLogger(SseTokenFilter::class.java)

    override fun getOrder(): Int = io.micronaut.core.order.Ordered.HIGHEST_PRECEDENCE

    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain,
    ): Publisher<MutableHttpResponse<*>> {
        // Check if token is provided in query parameter
        val tokenParam = request.parameters.get("token", String::class.java)

        return if (tokenParam.isPresent) {
            val token = tokenParam.get()
            val userId = sseTokenService.validateToken(token)

            if (userId != null) {
                // Token is valid - fetch user and create authentication
                val user = userRepository.findById(userId).orElse(null)

                if (user != null) {
                    // Create authentication object for Micronaut Security
                    val roles = if (user.isAdmin) listOf("admin", "user") else listOf("user")
                    val attributes =
                        mapOf(
                            "userId" to requireNotNull(user.id) { "User ID must not be null" },
                            "greeting" to user.greeting,
                            "isAdmin" to user.isAdmin,
                        )

                    val authentication =
                        io.micronaut.security.authentication.Authentication.build(
                            user.userName,
                            roles,
                            attributes,
                        )

                    // Add authentication to request attributes for SecurityService
                    val modifiedRequest =
                        request
                            .mutate()
                            .setAttribute("micronaut.security.AUTHENTICATION", authentication)

                    log.trace("SSE token validated for user {}", userId)
                    chain.proceed(modifiedRequest)
                } else {
                    log.debug("User {} not found for valid SSE token", userId)
                    chain.proceed(request)
                }
            } else {
                log.debug("Invalid or expired SSE token")
                chain.proceed(request)
            }
        } else {
            log.debug("No token found in query parameter for SSE request")
            chain.proceed(request)
        }
    }
}
