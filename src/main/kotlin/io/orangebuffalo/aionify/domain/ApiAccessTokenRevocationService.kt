package io.orangebuffalo.aionify.domain

import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.transaction.annotation.TransactionalEventListener
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration

/**
 * Notifies long-lived API connections (such as event streams) about revoked API access tokens,
 * so that they are closed instead of keeping the access granted by a token that is no longer valid.
 */
@Singleton
open class ApiAccessTokenRevocationService {
    private val log = LoggerFactory.getLogger(ApiAccessTokenRevocationService::class.java)

    @Inject
    lateinit var eventPublisher: ApplicationEventPublisher<Any>

    private val revokedTokenIds = Sinks.many().multicast().directBestEffort<Long>()

    companion object {
        private val EMIT_RETRY_DURATION: Duration = Duration.ofSeconds(1)
    }

    /**
     * Announces that the token was deleted or its value was changed, once the current transaction commits.
     */
    fun tokenRevoked(tokenId: Long) {
        eventPublisher.publishEvent(ApiAccessTokenRevoked(tokenId))
    }

    @TransactionalEventListener(TransactionalEventListener.TransactionPhase.AFTER_COMMIT)
    open fun onApiAccessTokenRevoked(event: ApiAccessTokenRevoked) {
        log.debug("API token {} revoked, notifying {} subscriber(s)", event.tokenId, revokedTokenIds.currentSubscriberCount())
        try {
            revokedTokenIds.emitNext(event.tokenId, Sinks.EmitFailureHandler.busyLooping(EMIT_RETRY_DURATION))
        } catch (e: Exception) {
            log.error("Failed to publish revocation of API token {}", event.tokenId, e)
        }
    }

    /**
     * Emits when the given token is revoked.
     */
    fun revocations(tokenId: Long): Flux<Long> = revokedTokenIds.asFlux().filter { it == tokenId }
}

data class ApiAccessTokenRevoked(
    val tokenId: Long,
)
