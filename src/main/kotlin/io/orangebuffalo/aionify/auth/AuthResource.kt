package io.orangebuffalo.aionify.auth

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.security.Principal

@Controller("/api/auth")
open class AuthResource(
    private val authService: AuthService,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(AuthResource::class.java)

    @Post("/login")
    @Secured(SecurityRule.IS_ANONYMOUS)
    open fun login(
        @Valid @Body request: LoginRequest,
    ): HttpResponse<*> =
        try {
            val response = authService.authenticate(request.userName, request.password)
            log.trace("Login endpoint returned success for user: {}", request.userName)
            HttpResponse.ok(response)
        } catch (e: AuthenticationException) {
            log.trace("Login endpoint returned unauthorized for user: {}", request.userName)
            HttpResponse
                .unauthorized<LoginErrorResponse>()
                .body(LoginErrorResponse(e.message ?: "Authentication failed"))
        }

    @Post("/change-password")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    open fun changePassword(
        @Valid @Body request: ChangePasswordRequest,
        principal: Principal?,
    ): HttpResponse<*> {
        val userName = principal?.name
        if (userName == null) {
            log.debug("Change password failed: user not authenticated")
            return HttpResponse
                .unauthorized<ChangePasswordErrorResponse>()
                .body(ChangePasswordErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        return try {
            authService.changePassword(userName, request.currentPassword, request.newPassword)
            log.trace("Change password endpoint returned success for user: {}", userName)
            HttpResponse.ok(ChangePasswordSuccessResponse("Password changed successfully"))
        } catch (e: AuthenticationException) {
            val errorCode =
                when (e.message) {
                    "Current password is incorrect" -> "CURRENT_PASSWORD_INCORRECT"
                    else -> "UNKNOWN_ERROR"
                }
            log.trace("Change password endpoint returned error for user: {}, errorCode: {}", userName, errorCode)
            HttpResponse.badRequest(ChangePasswordErrorResponse(e.message ?: "Failed to change password", errorCode))
        }
    }
}

@Serdeable
@Introspected
data class LoginRequest(
    val userName: String,
    val password: String,
)

@Serdeable
@Introspected
data class LoginResponse(
    val token: String,
    val userName: String,
    val greeting: String,
    val admin: Boolean,
    val languageCode: String,
)

@Serdeable
@Introspected
data class LoginErrorResponse(
    val error: String,
    val errorCode: String = "INVALID_CREDENTIALS",
)

@Serdeable
@Introspected
data class ChangePasswordRequest(
    @field:NotBlank(message = "Current password cannot be empty")
    val currentPassword: String,
    @field:NotBlank(message = "New password cannot be empty")
    @field:Size(max = 50, message = "Password cannot exceed 50 characters")
    val newPassword: String,
)

@Serdeable
@Introspected
data class ChangePasswordSuccessResponse(
    val message: String,
)

@Serdeable
@Introspected
data class ChangePasswordErrorResponse(
    val error: String,
    val errorCode: String,
)
