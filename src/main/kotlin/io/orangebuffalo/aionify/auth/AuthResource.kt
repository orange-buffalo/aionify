package io.orangebuffalo.aionify.auth

import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.cookie.Cookie
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Hidden
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.security.Principal
import java.time.Duration

@Controller("/api-ui/auth")
@Hidden
open class AuthResource(
    private val authService: AuthService,
    private val rememberMeService: RememberMeService,
    @Property(name = "aionify.auth.remember-me.cookie-name", defaultValue = "aionify_remember_me")
    private val rememberMeCookieName: String,
    @Property(name = "aionify.auth.remember-me.expiration-days", defaultValue = "30")
    private val rememberMeExpirationDays: Int,
    @Property(name = "aionify.auth.remember-me.secure", defaultValue = "true")
    private val rememberMeSecure: Boolean,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(AuthResource::class.java)

    @Post("/login")
    @Secured(SecurityRule.IS_ANONYMOUS)
    open fun login(
        @Valid @Body request: LoginRequest,
        httpRequest: HttpRequest<*>,
    ): HttpResponse<*> =
        try {
            val response = authService.authenticate(request.userName, request.password)
            log.trace("Login endpoint returned success for user: {}", request.userName)

            var httpResponse = HttpResponse.ok(response)

            // Handle remember me
            if (request.rememberMe) {
                val userAgent = httpRequest.headers.get("User-Agent")
                val (token, _) = rememberMeService.generateToken(response.userId, userAgent)

                val cookie =
                    Cookie
                        .of(rememberMeCookieName, token)
                        .httpOnly(true)
                        .secure(rememberMeSecure)
                        .sameSite(io.micronaut.http.cookie.SameSite.Strict)
                        .maxAge(Duration.ofDays(rememberMeExpirationDays.toLong()))
                        .path("/")

                httpResponse = httpResponse.cookie(cookie)
                log.debug("Set remember me cookie for user: {}", request.userName)
            }

            httpResponse
        } catch (e: AuthenticationException) {
            log.trace("Login endpoint returned unauthorized for user: {}", request.userName)
            HttpResponse
                .unauthorized<LoginErrorResponse>()
                .body(LoginErrorResponse(e.message ?: "Authentication failed"))
        }

    @Post("/logout")
    @Secured(SecurityRule.IS_ANONYMOUS)
    open fun logout(
        httpRequest: HttpRequest<*>,
        principal: Principal?,
    ): HttpResponse<*> {
        // Clear remember me cookie if present
        val rememberMeCookie = httpRequest.cookies.get(rememberMeCookieName)
        var httpResponse = HttpResponse.ok<Unit>()

        if (rememberMeCookie != null) {
            rememberMeService.invalidateToken(rememberMeCookie.value)

            // Clear the cookie
            val clearCookie =
                Cookie
                    .of(rememberMeCookieName, "")
                    .httpOnly(true)
                    .secure(rememberMeSecure)
                    .sameSite(io.micronaut.http.cookie.SameSite.Strict)
                    .maxAge(Duration.ZERO)
                    .path("/")

            httpResponse = httpResponse.cookie(clearCookie)
            log.debug("Cleared remember me cookie for user: {}", principal?.name)
        }

        return httpResponse
    }

    @Post("/auto-login")
    @Secured(SecurityRule.IS_ANONYMOUS)
    open fun autoLogin(httpRequest: HttpRequest<*>): HttpResponse<*> {
        // Check for remember me cookie
        val rememberMeCookie = httpRequest.cookies.get(rememberMeCookieName)
        if (rememberMeCookie == null) {
            log.trace("Auto-login: no remember me cookie found")
            return HttpResponse
                .unauthorized<LoginErrorResponse>()
                .body(LoginErrorResponse("No remember me cookie"))
        }

        // Validate the remember me token
        val token = rememberMeCookie.value
        val userAgent = httpRequest.headers.get("User-Agent")
        val userId = rememberMeService.validateToken(token, userAgent)

        if (userId == null) {
            log.debug("Auto-login: invalid or expired remember me token")
            return HttpResponse
                .unauthorized<LoginErrorResponse>()
                .body(LoginErrorResponse("Invalid or expired remember me token"))
        }

        // Generate a JWT token for the user
        val response = authService.authenticateById(userId)
        log.info("Auto-login successful for user ID: {}", userId)
        return HttpResponse.ok(response)
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

    @Post("/refresh")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    open fun refresh(principal: Principal?): HttpResponse<*> {
        val userName = principal?.name
        if (userName == null) {
            log.debug("Token refresh failed: user not authenticated")
            return HttpResponse
                .unauthorized<RefreshTokenErrorResponse>()
                .body(RefreshTokenErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        return try {
            val response = authService.refreshToken(userName)
            log.trace("Token refresh endpoint returned success for user: {}", userName)
            HttpResponse.ok(response)
        } catch (e: AuthenticationException) {
            log.debug("Token refresh failed for user: {}", userName, e)
            HttpResponse
                .unauthorized<RefreshTokenErrorResponse>()
                .body(RefreshTokenErrorResponse(e.message ?: "Token refresh failed", "TOKEN_REFRESH_FAILED"))
        }
    }
}

@Serdeable
@Introspected
data class LoginRequest(
    val userName: String,
    val password: String,
    val rememberMe: Boolean = false,
)

@Serdeable
@Introspected
data class LoginResponse(
    val token: String,
    val userName: String,
    val greeting: String,
    val admin: Boolean,
    val languageCode: String,
    val userId: Long,
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

@Serdeable
@Introspected
data class RefreshTokenResponse(
    val token: String,
)

@Serdeable
@Introspected
data class RefreshTokenErrorResponse(
    val error: String,
    val errorCode: String,
)
