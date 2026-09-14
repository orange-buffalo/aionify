package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Hidden
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Instant

/**
 * Manages the current user's named API access tokens.
 * Users can have multiple tokens (e.g. one per integration), so that each of them can be revoked independently.
 */
@Controller("/api-ui/users/api-tokens")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Transactional
@Hidden
open class UserApiAccessTokenResource(
    private val userApiAccessTokenRepository: UserApiAccessTokenRepository,
    private val timeService: TimeService,
) {
    private val log = LoggerFactory.getLogger(UserApiAccessTokenResource::class.java)
    private val secureRandom = SecureRandom()

    companion object {
        const val MAX_TOKENS_PER_USER = 20
        const val MAX_NAME_LENGTH = 100
        private const val TOKEN_LENGTH = 50
        private const val TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    }

    @Get
    open fun listTokens(currentUser: UserWithId): HttpResponse<*> {
        val tokens =
            userApiAccessTokenRepository
                .findAllByUserId(currentUser.id)
                .sortedWith(compareBy<UserApiAccessToken> { it.createdAt }.thenBy { it.id })
                .map { it.toSummary() }

        return HttpResponse.ok(ApiAccessTokensResponse(tokens = tokens))
    }

    @Post
    open fun createToken(
        @Body request: CreateApiAccessTokenRequest,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        val name = request.name?.trim() ?: ""
        if (name.isEmpty()) {
            return HttpResponse.badRequest(ApiAccessTokenErrorResponse("Token name is required", "API_TOKEN_NAME_REQUIRED"))
        }
        if (name.length > MAX_NAME_LENGTH) {
            return HttpResponse.badRequest(
                ApiAccessTokenErrorResponse("Token name cannot exceed $MAX_NAME_LENGTH characters", "API_TOKEN_NAME_TOO_LONG"),
            )
        }
        if (userApiAccessTokenRepository.existsByUserIdAndName(currentUser.id, name)) {
            log.debug("Create API token failed: name '{}' already used by user: {}", name, currentUser.user.userName)
            return HttpResponse.badRequest(
                ApiAccessTokenErrorResponse("API token with this name already exists", "API_TOKEN_NAME_ALREADY_EXISTS"),
            )
        }
        if (userApiAccessTokenRepository.countByUserId(currentUser.id) >= MAX_TOKENS_PER_USER) {
            log.debug("Create API token failed: token limit reached for user: {}", currentUser.user.userName)
            return HttpResponse.badRequest(
                ApiAccessTokenErrorResponse("Maximum number of API tokens reached", "API_TOKEN_LIMIT_REACHED"),
            )
        }

        val token =
            userApiAccessTokenRepository.save(
                UserApiAccessToken(
                    userId = currentUser.id,
                    token = generateRandomToken(),
                    name = name,
                    createdAt = timeService.now(),
                ),
            )
        log.info("API token '{}' created for user: {}", name, currentUser.user.userName)

        return HttpResponse.ok(
            CreatedApiAccessTokenResponse(
                id = requireNotNull(token.id),
                name = token.name,
                createdAt = token.createdAt,
                token = token.token,
            ),
        )
    }

    @Get("/{id}/value")
    open fun getTokenValue(
        @PathVariable id: Long,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        val token = findOwnedToken(id, currentUser) ?: return tokenNotFound()

        return HttpResponse.ok(ApiAccessTokenValueResponse(token = token.token))
    }

    @Put("/{id}")
    open fun regenerateToken(
        @PathVariable id: Long,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        val token = findOwnedToken(id, currentUser) ?: return tokenNotFound()

        val updatedToken = userApiAccessTokenRepository.update(token.copy(token = generateRandomToken()))
        log.info("API token '{}' regenerated for user: {}", token.name, currentUser.user.userName)

        return HttpResponse.ok(updatedToken.toSummary())
    }

    @Delete("/{id}")
    open fun deleteToken(
        @PathVariable id: Long,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        val token = findOwnedToken(id, currentUser) ?: return tokenNotFound()

        userApiAccessTokenRepository.delete(token)
        log.info("API token '{}' deleted for user: {}", token.name, currentUser.user.userName)

        return HttpResponse.ok(ApiAccessTokenSuccessResponse("API token deleted successfully"))
    }

    private fun findOwnedToken(
        id: Long,
        currentUser: UserWithId,
    ): UserApiAccessToken? = userApiAccessTokenRepository.findByIdAndUserId(id, currentUser.id).orElse(null)

    private fun tokenNotFound(): HttpResponse<ApiAccessTokenErrorResponse> =
        HttpResponse
            .notFound<ApiAccessTokenErrorResponse>()
            .body(ApiAccessTokenErrorResponse("API token not found", "API_TOKEN_NOT_FOUND"))

    private fun generateRandomToken(): String =
        (1..TOKEN_LENGTH)
            .map { TOKEN_CHARS[secureRandom.nextInt(TOKEN_CHARS.length)] }
            .joinToString("")

    private fun UserApiAccessToken.toSummary() =
        ApiAccessTokenSummary(
            id = requireNotNull(id),
            name = name,
            createdAt = createdAt,
        )
}

@Serdeable
@Introspected
data class ApiAccessTokenSummary(
    val id: Long,
    val name: String,
    val createdAt: Instant,
)

@Serdeable
@Introspected
data class ApiAccessTokensResponse(
    val tokens: List<ApiAccessTokenSummary>,
)

@Serdeable
@Introspected
data class CreateApiAccessTokenRequest(
    val name: String? = null,
)

@Serdeable
@Introspected
data class CreatedApiAccessTokenResponse(
    val id: Long,
    val name: String,
    val createdAt: Instant,
    val token: String,
)

@Serdeable
@Introspected
data class ApiAccessTokenValueResponse(
    val token: String,
)

@Serdeable
@Introspected
data class ApiAccessTokenSuccessResponse(
    val message: String,
)

@Serdeable
@Introspected
data class ApiAccessTokenErrorResponse(
    val error: String,
    val errorCode: String,
)
