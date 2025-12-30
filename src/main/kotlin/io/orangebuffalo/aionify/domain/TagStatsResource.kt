package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import jakarta.transaction.Transactional
import java.security.Principal

@Controller("/api/tags")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Transactional
open class TagStatsResource(
    private val tagStatsRepository: TagStatsRepository,
    private val userRepository: UserRepository,
    private val legacyTagRepository: LegacyTagRepository,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(TagStatsResource::class.java)

    @Get("/stats")
    open fun getTagStats(currentUser: UserWithId): HttpResponse<*> {
        log.debug("Getting tag statistics for user: {}", currentUser.user.userName)

        val tagStats = tagStatsRepository.findTagStatsByOwnerId(currentUser.id)

        log.trace("Found {} unique tags for user: {}", tagStats.size, currentUser.user.userName)

        return HttpResponse.ok(
            TagStatsResponse(
                tags = tagStats.map { TagStatDto(tag = it.tag, count = it.count, isLegacy = it.isLegacy) },
            ),
        )
    }

    @Post("/legacy")
    open fun markTagAsLegacy(
        @Body request: LegacyTagRequest,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        // Check if already marked as legacy
        val existing = legacyTagRepository.findByUserIdAndName(currentUser.id, request.tag)
        if (existing.isPresent) {
            log.debug("Tag '{}' is already marked as legacy for user: {}", request.tag, currentUser.user.userName)
            return HttpResponse.ok(LegacyTagResponse("Tag is already marked as legacy"))
        }

        log.debug("Marking tag '{}' as legacy for user: {}", request.tag, currentUser.user.userName)

        legacyTagRepository.save(
            LegacyTag(
                userId = currentUser.id,
                name = request.tag,
            ),
        )

        return HttpResponse.ok(LegacyTagResponse("Tag marked as legacy successfully"))
    }

    @Delete("/legacy")
    open fun unmarkTagAsLegacy(
        @Body request: LegacyTagRequest,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug("Unmarking tag '{}' as legacy for user: {}", request.tag, currentUser.user.userName)

        val deletedCount = legacyTagRepository.deleteByUserIdAndName(currentUser.id, request.tag)

        if (deletedCount == 0L) {
            log.debug("Tag '{}' was not marked as legacy for user: {}", request.tag, currentUser.user.userName)
        }

        return HttpResponse.ok(LegacyTagResponse("Tag unmarked as legacy successfully"))
    }
}

@Serdeable
@Introspected
data class TagStatDto(
    val tag: String,
    val count: Long,
    val isLegacy: Boolean,
)

@Serdeable
@Introspected
data class TagStatsResponse(
    val tags: List<TagStatDto>,
)

@Serdeable
@Introspected
data class LegacyTagRequest(
    val tag: String,
)

@Serdeable
@Introspected
data class LegacyTagResponse(
    val message: String,
)
