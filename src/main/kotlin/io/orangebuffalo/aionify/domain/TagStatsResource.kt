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
    private val legacyTagRepository: LegacyTagRepository
) {

    private val log = org.slf4j.LoggerFactory.getLogger(TagStatsResource::class.java)

    @Get("/stats")
    open fun getTagStats(principal: Principal?): HttpResponse<*> {
        val userName = principal?.name
        if (userName == null) {
            log.debug("Get tag stats failed: user not authenticated")
            return HttpResponse.unauthorized<TagStatsErrorResponse>()
                .body(TagStatsErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Get tag stats failed: user not found: {}", userName)
            return HttpResponse.notFound<TagStatsErrorResponse>()
                .body(TagStatsErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        val userId = requireNotNull(user.id) { "User must have an ID" }

        log.debug("Getting tag statistics for user: {}", userName)
        
        val tagStats = tagStatsRepository.findTagStatsByOwnerId(userId)

        log.trace("Found {} unique tags for user: {}", tagStats.size, userName)

        return HttpResponse.ok(
            TagStatsResponse(
                tags = tagStats.map { TagStatDto(tag = it.tag, count = it.count, isLegacy = it.isLegacy) }
            )
        )
    }

    @Post("/legacy")
    open fun markTagAsLegacy(@Body request: MarkLegacyTagRequest, principal: Principal?): HttpResponse<*> {
        val userName = principal?.name
        if (userName == null) {
            log.debug("Mark tag as legacy failed: user not authenticated")
            return HttpResponse.unauthorized<TagStatsErrorResponse>()
                .body(TagStatsErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Mark tag as legacy failed: user not found: {}", userName)
            return HttpResponse.notFound<TagStatsErrorResponse>()
                .body(TagStatsErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        val userId = requireNotNull(user.id) { "User must have an ID" }

        // Check if already marked as legacy
        val existing = legacyTagRepository.findByUserIdAndName(userId, request.tag)
        if (existing.isPresent) {
            log.debug("Tag '{}' is already marked as legacy for user: {}", request.tag, userName)
            return HttpResponse.ok(MarkLegacyTagResponse("Tag is already marked as legacy"))
        }

        log.debug("Marking tag '{}' as legacy for user: {}", request.tag, userName)
        
        legacyTagRepository.save(
            LegacyTag(
                userId = userId,
                name = request.tag
            )
        )

        return HttpResponse.ok(MarkLegacyTagResponse("Tag marked as legacy successfully"))
    }

    @Delete("/legacy")
    open fun unmarkTagAsLegacy(@Body request: UnmarkLegacyTagRequest, principal: Principal?): HttpResponse<*> {
        val userName = principal?.name
        if (userName == null) {
            log.debug("Unmark tag as legacy failed: user not authenticated")
            return HttpResponse.unauthorized<TagStatsErrorResponse>()
                .body(TagStatsErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Unmark tag as legacy failed: user not found: {}", userName)
            return HttpResponse.notFound<TagStatsErrorResponse>()
                .body(TagStatsErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        val userId = requireNotNull(user.id) { "User must have an ID" }

        log.debug("Unmarking tag '{}' as legacy for user: {}", request.tag, userName)
        
        val deletedCount = legacyTagRepository.deleteByUserIdAndName(userId, request.tag)
        
        if (deletedCount == 0L) {
            log.debug("Tag '{}' was not marked as legacy for user: {}", request.tag, userName)
        }

        return HttpResponse.ok(UnmarkLegacyTagResponse("Tag unmarked as legacy successfully"))
    }
}

@Serdeable
@Introspected
data class TagStatDto(
    val tag: String,
    val count: Long,
    val isLegacy: Boolean
)

@Serdeable
@Introspected
data class TagStatsResponse(
    val tags: List<TagStatDto>
)

@Serdeable
@Introspected
data class TagStatsErrorResponse(
    val error: String,
    val errorCode: String
)

@Serdeable
@Introspected
data class MarkLegacyTagRequest(
    val tag: String
)

@Serdeable
@Introspected
data class MarkLegacyTagResponse(
    val message: String
)

@Serdeable
@Introspected
data class UnmarkLegacyTagRequest(
    val tag: String
)

@Serdeable
@Introspected
data class UnmarkLegacyTagResponse(
    val message: String
)
