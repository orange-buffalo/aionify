package io.orangebuffalo.aionify.domain

import io.quarkus.security.Authenticated
import jakarta.annotation.security.RolesAllowed
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import kotlinx.serialization.Serializable

@Path("/api/admin/users")
@RolesAllowed("admin")
class UserAdminResource(private val userRepository: UserRepository) {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    fun listUsers(
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int
    ): Response {
        if (page < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse("Page must be non-negative"))
                .build()
        }
        
        if (size <= 0 || size > 100) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse("Size must be between 1 and 100"))
                .build()
        }
        
        val pagedUsers = userRepository.findAllPaginated(page, size)
        
        return Response.ok(
            UsersListResponse(
                users = pagedUsers.users.map { user ->
                    UserDto(
                        id = requireNotNull(user.id) { "User must have an ID" },
                        userName = user.userName,
                        greeting = user.greeting,
                        isAdmin = user.isAdmin
                    )
                },
                total = pagedUsers.total,
                page = pagedUsers.page,
                size = pagedUsers.size
            )
        ).build()
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    fun deleteUser(@PathParam("id") id: Long, @Context securityContext: SecurityContext): Response {
        val currentUserName = securityContext.userPrincipal?.name
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse("User not authenticated"))
                .build()
        
        val currentUser = userRepository.findByUserName(currentUserName)
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse("User not found"))
                .build()
        
        // Prevent self-deletion
        if (currentUser.id == id) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse("Cannot delete your own user account"))
                .build()
        }
        
        val deleted = userRepository.deleteById(id)
        
        return if (deleted) {
            Response.ok(SuccessResponse("User deleted successfully")).build()
        } else {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse("User not found"))
                .build()
        }
    }
}

@Serializable
data class UserDto(
    val id: Long,
    val userName: String,
    val greeting: String,
    val isAdmin: Boolean
)

@Serializable
data class UsersListResponse(
    val users: List<UserDto>,
    val total: Long,
    val page: Int,
    val size: Int
)

@Serializable
data class SuccessResponse(
    val message: String
)

@Serializable
data class ErrorResponse(
    val error: String
)
