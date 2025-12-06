package io.orangebuffalo.aionify.auth

import io.quarkus.security.Authenticated
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext

@Path("/api/auth")
class AuthResource(private val authService: AuthService) {

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun login(@Valid request: LoginRequest): Response {
        return try {
            val response = authService.authenticate(request.userName, request.password)
            Response.ok(response).build()
        } catch (e: AuthenticationException) {
            Response.status(Response.Status.UNAUTHORIZED)
                .entity(LoginErrorResponse(e.message ?: "Authentication failed"))
                .build()
        }
    }

    @POST
    @Path("/change-password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    fun changePassword(@Valid request: ChangePasswordRequest, @Context securityContext: SecurityContext): Response {
        val userName = securityContext.userPrincipal?.name
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ChangePasswordErrorResponse("User not authenticated"))
                .build()

        return try {
            authService.changePassword(userName, request.currentPassword, request.newPassword)
            Response.ok(ChangePasswordSuccessResponse("Password changed successfully")).build()
        } catch (e: AuthenticationException) {
            Response.status(Response.Status.BAD_REQUEST)
                .entity(ChangePasswordErrorResponse(e.message ?: "Failed to change password"))
                .build()
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
    val isAdmin: Boolean,
    val languageCode: String
)

data class LoginErrorResponse(
    val error: String
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
    val error: String
)
