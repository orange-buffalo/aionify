package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import jakarta.transaction.Transactional
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Controller("/api/activation")
@Secured(SecurityRule.IS_ANONYMOUS)
@Transactional
open class ActivationResource(
    private val activationTokenService: ActivationTokenService,
    private val rateLimitingService: RateLimitingService,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(ActivationResource::class.java)

    @Get("/validate")
    open fun validateToken(
        @QueryValue token: String,
        request: HttpRequest<*>,
    ): HttpResponse<*> {
        val clientId = getClientIdentifier(request, token)

        // Check rate limiting
        if (rateLimitingService.isRateLimited(clientId)) {
            log.debug("Token validation blocked by rate limiting for client: {}", clientId)
            return HttpResponse
                .status<ValidateTokenErrorResponse>(HttpStatus.TOO_MANY_REQUESTS)
                .body(
                    ValidateTokenErrorResponse(
                        error = "Too many validation attempts. Please try again later.",
                        errorCode = "RATE_LIMIT_EXCEEDED",
                    ),
                )
        }

        // Record attempt
        rateLimitingService.recordAttempt(clientId)

        // Validate token
        val user = activationTokenService.validateToken(token)

        return if (user != null) {
            log.trace("Token validation successful for user: {}", user.userName)
            HttpResponse.ok(
                ValidateTokenResponse(
                    valid = true,
                    userName = user.userName,
                    greeting = user.greeting,
                ),
            )
        } else {
            log.debug("Token validation failed: invalid or expired token")
            HttpResponse.ok(
                ValidateTokenResponse(
                    valid = false,
                    userName = null,
                    greeting = null,
                ),
            )
        }
    }

    @Post("/set-password")
    open fun setPassword(
        @Valid @Body request: SetPasswordRequest,
        httpRequest: HttpRequest<*>,
    ): HttpResponse<*> {
        val clientId = getClientIdentifier(httpRequest, request.token)

        // Check rate limiting
        if (rateLimitingService.isRateLimited(clientId)) {
            log.debug("Set password blocked by rate limiting for client: {}", clientId)
            return HttpResponse
                .status<SetPasswordErrorResponse>(HttpStatus.TOO_MANY_REQUESTS)
                .body(
                    SetPasswordErrorResponse(
                        error = "Too many attempts. Please try again later.",
                        errorCode = "RATE_LIMIT_EXCEEDED",
                    ),
                )
        }

        // Record attempt
        rateLimitingService.recordAttempt(clientId)

        // Set password
        val success = activationTokenService.setPasswordWithToken(request.token, request.password)

        return if (success) {
            // Clear rate limiting on success
            rateLimitingService.clearAttempts(clientId)
            log.trace("Set password endpoint returned success")
            HttpResponse.ok(SetPasswordSuccessResponse("Password set successfully"))
        } else {
            log.debug("Set password failed: invalid or expired token")
            HttpResponse.badRequest(
                SetPasswordErrorResponse(
                    error = "Invalid or expired token",
                    errorCode = "INVALID_TOKEN",
                ),
            )
        }
    }

    private fun getClientIdentifier(
        request: HttpRequest<*>,
        token: String,
    ): String {
        // Use token as part of the identifier to rate limit per token
        return "activation:$token:${request.remoteAddress.address.hostAddress}"
    }
}

@Serdeable
@Introspected
data class ValidateTokenResponse(
    val valid: Boolean,
    val userName: String?,
    val greeting: String?,
)

@Serdeable
@Introspected
data class ValidateTokenErrorResponse(
    val error: String,
    val errorCode: String,
)

@Serdeable
@Introspected
data class SetPasswordRequest(
    @field:NotBlank(message = "Token is required")
    val token: String,
    @field:NotBlank(message = "Password cannot be empty")
    @field:Size(max = 50, message = "Password cannot exceed 50 characters")
    val password: String,
)

@Serdeable
@Introspected
data class SetPasswordSuccessResponse(
    val message: String,
)

@Serdeable
@Introspected
data class SetPasswordErrorResponse(
    val error: String,
    val errorCode: String,
)
