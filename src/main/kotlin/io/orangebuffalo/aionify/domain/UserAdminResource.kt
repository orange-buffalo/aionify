package io.orangebuffalo.aionify.domain

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import java.security.Principal

@Controller("/api/admin/users")
@Secured("admin")
class UserAdminResource(
    private val userRepository: UserRepository,
    private val userService: UserService
) {

    @Get
    fun listUsers(
        @QueryValue(defaultValue = "0") page: Int,
        @QueryValue(defaultValue = "20") size: Int
    ): HttpResponse<*> {
        if (page < 0) {
            return HttpResponse.badRequest(ErrorResponse("Page must be non-negative"))
        }
        
        if (size <= 0 || size > 100) {
            return HttpResponse.badRequest(ErrorResponse("Size must be between 1 and 100"))
        }
        
        val pagedUsers = userService.findAllPaginated(page, size)
        
        return HttpResponse.ok(
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
        )
    }

    @Delete("/{id}")
    fun deleteUser(@PathVariable id: Long, principal: Principal?): HttpResponse<*> {
        val currentUserName = principal?.name
            ?: return HttpResponse.unauthorized<ErrorResponse>()
                .body(ErrorResponse("User not authenticated"))
        
        val currentUser = userRepository.findByUserName(currentUserName).orElse(null)
            ?: return HttpResponse.unauthorized<ErrorResponse>()
                .body(ErrorResponse("User not found"))
        
        // Prevent self-deletion
        if (currentUser.id == id) {
            return HttpResponse.badRequest(ErrorResponse("Cannot delete your own user account"))
        }
        
        val deleted = userRepository.deleteById(id).isPresent
        
        return if (deleted) {
            HttpResponse.ok(SuccessResponse("User deleted successfully"))
        } else {
            HttpResponse.notFound<ErrorResponse>()
                .body(ErrorResponse("User not found"))
        }
    }
}

data class UserDto(
    val id: Long,
    val userName: String,
    val greeting: String,
    val isAdmin: Boolean
)

data class UsersListResponse(
    val users: List<UserDto>,
    val total: Long,
    val page: Int,
    val size: Int
)

data class SuccessResponse(
    val message: String
)

data class ErrorResponse(
    val error: String
)
