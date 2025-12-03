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
