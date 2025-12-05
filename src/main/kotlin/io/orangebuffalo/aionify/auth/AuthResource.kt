package io.orangebuffalo.aionify.auth

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/api/auth")
class AuthResource(private val authService: AuthService) {

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun login(request: LoginRequest): Response {
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
    fun changePassword(request: ChangePasswordRequest): Response {
        // Validate current password is not empty
        if (request.currentPassword.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ChangePasswordErrorResponse("Current password cannot be empty"))
                .build()
        }
        // Validate new password is not empty and within max length
        if (request.newPassword.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ChangePasswordErrorResponse("New password cannot be empty"))
                .build()
        }
        if (request.newPassword.length > 50) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ChangePasswordErrorResponse("Password cannot exceed 50 characters"))
                .build()
        }
        if (request.newPassword != request.confirmPassword) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ChangePasswordErrorResponse("New password and confirmation do not match"))
                .build()
        }

        return try {
            authService.changePassword(request.userName, request.currentPassword, request.newPassword)
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
    val isAdmin: Boolean
)

data class LoginErrorResponse(
    val error: String
)

data class ChangePasswordRequest(
    val userName: String,
    val currentPassword: String,
    val newPassword: String,
    val confirmPassword: String
)

data class ChangePasswordSuccessResponse(
    val message: String
)

data class ChangePasswordErrorResponse(
    val error: String
)
