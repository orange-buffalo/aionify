package io.orangebuffalo.aionify.domain

import io.micronaut.http.sse.Event
import reactor.core.publisher.Flux
import java.time.Duration

/**
 * Heartbeat events for Server-Sent Events streams: one is sent immediately on connection (to confirm that
 * the stream is working) and then periodically, so that clients can detect stale connections.
 */
object SseHeartbeat {
    private val INTERVAL: Duration = Duration.ofSeconds(30)

    fun flux(): Flux<Event<String>> =
        Flux.concat(
            Flux.just(heartbeatEvent()),
            Flux.interval(INTERVAL).map { heartbeatEvent() },
        )

    private fun heartbeatEvent(): Event<String> = Event.of("heartbeat").name("heartbeat")
}
