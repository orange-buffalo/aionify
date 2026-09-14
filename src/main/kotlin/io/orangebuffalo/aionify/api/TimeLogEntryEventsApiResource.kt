package io.orangebuffalo.aionify.api

import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.sse.Event
import io.micronaut.serde.annotation.Serdeable
import io.orangebuffalo.aionify.domain.ApiAccessTokenRevocationService
import io.orangebuffalo.aionify.domain.SseHeartbeat
import io.orangebuffalo.aionify.domain.TimeLogEntryEventService
import io.orangebuffalo.aionify.domain.UserApiAccessTokenRepository
import io.orangebuffalo.aionify.domain.UserWithId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.time.Duration

/**
 * Public API Server-Sent Events stream of time log entry changes.
 * Authenticated with a regular API token (see [ApiAuthenticationFilter]), so that native clients and other
 * integrations can subscribe without the short-lived query parameter tokens used by the web UI stream.
 *
 * Note: this controller is intentionally not transactional, as the stream outlives any transaction.
 */
@Controller("/api/time-log-entries")
@Tag(name = "Public API", description = "Public API endpoints")
open class TimeLogEntryEventsApiResource(
    private val eventService: TimeLogEntryEventService,
    private val tokenRevocationService: ApiAccessTokenRevocationService,
    private val userApiAccessTokenRepository: UserApiAccessTokenRepository,
    @Property(name = "aionify.api.token-revalidation-interval", defaultValue = "30s")
    private val tokenRevalidationInterval: Duration,
) {
    private val log = LoggerFactory.getLogger(TimeLogEntryEventsApiResource::class.java)

    @Get(uri = "/events", produces = [MediaType.TEXT_EVENT_STREAM])
    @Operation(
        summary = "Subscribe to time log entry events",
        description = """
            Opens a Server-Sent Events stream with changes to the authenticated user's time log entries,
            regardless of where the change was made (web UI, public API or another integration).
            Each change is sent as a default (unnamed) event with a JSON TimeLogEntryApiEvent as data.
            A "heartbeat" event is sent right after connecting and then every 30 seconds;
            clients should reconnect when no heartbeat is received within 45 seconds.
            Events that happen while a client is disconnected are not replayed: after reconnecting,
            clients should reload the state they need (e.g. the active entry).
            The stream is closed as soon as the API token used to open it is deleted or regenerated
            (or within 30 seconds, if the token becomes invalid otherwise).
            New event types and fields may be added in the future: clients must ignore unknown event types and fields.
        """,
        security = [SecurityRequirement(name = "BearerAuth")],
    )
    @ApiResponse(
        responseCode = "200",
        description = "Event stream",
        content = [
            Content(
                mediaType = MediaType.TEXT_EVENT_STREAM,
                schema = Schema(implementation = TimeLogEntryApiEvent::class),
            ),
        ],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized - Invalid or missing API token",
    )
    @ApiResponse(
        responseCode = "429",
        description = "Too Many Requests - IP blocked due to too many failed auth attempts",
    )
    open fun streamEvents(
        currentUser: UserWithId,
        request: HttpRequest<*>,
    ): Flux<Event<*>> {
        val userId = currentUser.id
        val tokenId =
            request
                .getAttribute(ApiAuthenticationFilter.API_ACCESS_TOKEN_ID_ATTRIBUTE, Long::class.javaObjectType)
                .orElseThrow { IllegalStateException("Event stream request is not authenticated with an API token") }
        val tokenValue =
            request
                .getAttribute(ApiAuthenticationFilter.API_ACCESS_TOKEN_VALUE_ATTRIBUTE, String::class.java)
                .orElseThrow { IllegalStateException("Event stream request is not authenticated with an API token") }
        log.debug("User {} subscribing to public time log entry events with API token {}", userId, tokenId)

        // Access is granted by the token the stream was opened with, so the stream must end once the token is revoked.
        // Revalidation covers tokens that change without a revocation notification and notifications missed at startup.
        val tokenRevoked =
            Flux.merge(
                tokenRevocationService.revocations(tokenId),
                Flux
                    .interval(Duration.ZERO, tokenRevalidationInterval)
                    .publishOn(Schedulers.boundedElastic())
                    .filter { !userApiAccessTokenRepository.existsByIdAndToken(tokenId, tokenValue) },
            )

        val entryEvents =
            eventService
                .getEventFlux(userId)
                .map { change ->
                    Event.of(
                        TimeLogEntryApiEvent(
                            type = change.eventType.name,
                            entry = change.entry.toApiDto(),
                        ),
                    )
                }

        return Flux
            .merge<Event<*>>(entryEvents, SseHeartbeat.flux())
            .takeUntilOther(tokenRevoked)
            .doOnComplete { log.debug("Closed event stream of user {}: API token {} was revoked", userId, tokenId) }
    }
}

@Serdeable
@Introspected
@Schema(description = "Change of a time log entry, delivered via the event stream")
data class TimeLogEntryApiEvent(
    @field:Schema(
        description = "Type of the change. Clients must ignore types they do not know.",
        example = "ENTRY_STARTED",
        allowableValues = ["ENTRY_STARTED", "ENTRY_STOPPED"],
    )
    val type: String,
    @field:Schema(description = "State of the entry after the change")
    val entry: TimeLogEntryApiDto,
)
