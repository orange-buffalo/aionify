package io.orangebuffalo.aionify.auth

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.security.Principal

@Controller("/api/auth")
open class AuthResource(private val authService: AuthService) {

    @Post("/login")
    @Secured(SecurityRule.IS_ANONYMOUS)
    open fun login(@Valid @Body request: LoginRequest): HttpResponse<*> {
        return try {
            val response = authService.authenticate(request.userName, request.password)
            HttpResponse.ok(response)
        } catch (e: AuthenticationException) {
            HttpResponse.unauthorized<LoginErrorResponse>()
                .body(LoginErrorResponse(e.message ?: "Authentication failed"))
        }
    }

    @Post("/change-password")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    open fun changePassword(@Valid @Body request: ChangePasswordRequest, principal: Principal?): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<ChangePasswordErrorResponse>()
                .body(ChangePasswordErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        return try {
            authService.changePassword(userName, request.currentPassword, request.newPassword)
            HttpResponse.ok(ChangePasswordSuccessResponse("Password changed successfully"))
        } catch (e: AuthenticationException) {
            val errorCode = when (e.message) {
                "Current password is incorrect" -> "CURRENT_PASSWORD_INCORRECT"
                else -> "UNKNOWN_ERROR"
            }
            HttpResponse.badRequest(ChangePasswordErrorResponse(e.message ?: "Failed to change password", errorCode))
        }
    }
}

data class LoginRequest(
    val userName: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val userName: String,
    val greeting: String,
    val admin: Boolean,
    val languageCode: String
)

data class LoginErrorResponse(
    val error: String,
    val errorCode: String = "INVALID_CREDENTIALS"
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "Current password cannot be empty")
    val currentPassword: String,

    @field:NotBlank(message = "New password cannot be empty")
    @field:Size(max = 50, message = "Password cannot exceed 50 characters")
    val newPassword: String
)

data class ChangePasswordSuccessResponse(
    val message: String
)

data class ChangePasswordErrorResponse(
    val error: String,
    val errorCode: String
)
