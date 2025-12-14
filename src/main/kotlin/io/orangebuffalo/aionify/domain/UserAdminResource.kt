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
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.security.Principal
import java.time.Instant

@Controller("/api/admin/users")
@Secured("admin")
open class UserAdminResource(
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val activationTokenRepository: ActivationTokenRepository,
    private val activationTokenService: ActivationTokenService
) {

    @Get
    open fun listUsers(
        @QueryValue(defaultValue = "0") page: Int,
        @QueryValue(defaultValue = "20") size: Int
    ): HttpResponse<*> {
        if (page < 0) {
            return HttpResponse.badRequest(ErrorResponse("Page must be non-negative", "INVALID_PAGE"))
        }
        
        if (size <= 0 || size > 100) {
            return HttpResponse.badRequest(ErrorResponse("Size must be between 1 and 100", "INVALID_SIZE"))
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

    @Get("/{id}")
    open fun getUser(@PathVariable id: Long): HttpResponse<*> {
        val user = userRepository.findById(id).orElse(null)
            ?: return HttpResponse.notFound<ErrorResponse>()
                .body(ErrorResponse("User not found", "USER_NOT_FOUND"))
        
        // Check for activation token
        val activationToken = activationTokenRepository.findByUserId(id).orElse(null)
        val activationTokenInfo = if (activationToken != null && activationToken.expiresAt.isAfter(Instant.now())) {
            ActivationTokenInfo(
                token = activationToken.token,
                expiresAt = activationToken.expiresAt
            )
        } else {
            null
        }
        
        return HttpResponse.ok(
            UserDetailDto(
                id = requireNotNull(user.id) { "User must have an ID" },
                userName = user.userName,
                greeting = user.greeting,
                isAdmin = user.isAdmin,
                activationToken = activationTokenInfo
            )
        )
    }

    @Put("/{id}")
    open fun updateUser(
        @PathVariable id: Long,
        @Valid @Body request: UpdateUserRequest,
        principal: Principal?
    ): HttpResponse<*> {
        val user = userRepository.findById(id).orElse(null)
            ?: return HttpResponse.notFound<ErrorResponse>()
                .body(ErrorResponse("User not found", "USER_NOT_FOUND"))
        
        // Check if username is being changed
        if (request.userName != user.userName) {
            // Check for username uniqueness
            val existingUser = userRepository.findByUserName(request.userName).orElse(null)
            if (existingUser != null) {
                return HttpResponse.badRequest(ErrorResponse("Username already exists", "USERNAME_ALREADY_EXISTS"))
            }
            
            // Update the user with new username using Micronaut Data's update
            userRepository.update(
                User(
                    id = user.id,
                    userName = request.userName,
                    passwordHash = user.passwordHash,
                    greeting = user.greeting,
                    isAdmin = user.isAdmin,
                    localeTag = user.localeTag,
                    languageCode = user.languageCode
                )
            )
        }
        
        return HttpResponse.ok(SuccessResponse("User updated successfully"))
    }

    @Post("/{id}/regenerate-activation-token")
    open fun regenerateActivationToken(@PathVariable id: Long): HttpResponse<*> {
        val user = userRepository.findById(id).orElse(null)
            ?: return HttpResponse.notFound<ErrorResponse>()
                .body(ErrorResponse("User not found", "USER_NOT_FOUND"))
        
        // Generate new activation token
        val activationToken = activationTokenService.createToken(requireNotNull(user.id))
        
        return HttpResponse.ok(
            ActivationTokenResponse(
                token = activationToken.token,
                expiresAt = activationToken.expiresAt
            )
        )
    }

    @Delete("/{id}")
    open fun deleteUser(@PathVariable id: Long, principal: Principal?): HttpResponse<*> {
        val currentUserName = principal?.name
            ?: return HttpResponse.unauthorized<ErrorResponse>()
                .body(ErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        
        val currentUser = userRepository.findByUserName(currentUserName).orElse(null)
            ?: return HttpResponse.unauthorized<ErrorResponse>()
                .body(ErrorResponse("User not found", "USER_NOT_FOUND"))
        
        // Prevent self-deletion
        if (currentUser.id == id) {
            return HttpResponse.badRequest(ErrorResponse("Cannot delete your own user account", "CANNOT_DELETE_SELF"))
        }
        
        val deleted = userRepository.existsById(id)
        if (deleted) {
            userRepository.deleteById(id)
        }
        
        return if (deleted) {
            HttpResponse.ok(SuccessResponse("User deleted successfully"))
        } else {
            HttpResponse.notFound<ErrorResponse>()
                .body(ErrorResponse("User not found", "USER_NOT_FOUND"))
        }
    }
}

@Serdeable
@Introspected
data class UserDto(
    val id: Long,
    val userName: String,
    val greeting: String,
    val isAdmin: Boolean
)

@Serdeable
@Introspected
data class UsersListResponse(
    val users: List<UserDto>,
    val total: Long,
    val page: Int,
    val size: Int
)

@Serdeable
@Introspected
data class UserDetailDto(
    val id: Long,
    val userName: String,
    val greeting: String,
    val isAdmin: Boolean,
    val activationToken: ActivationTokenInfo?
)

@Serdeable
@Introspected
data class ActivationTokenInfo(
    val token: String,
    val expiresAt: Instant
)

@Serdeable
@Introspected
data class UpdateUserRequest(
    @field:NotBlank(message = "Username cannot be blank")
    @field:Size(max = 255, message = "Username cannot exceed 255 characters")
    val userName: String
)

@Serdeable
@Introspected
data class ActivationTokenResponse(
    val token: String,
    val expiresAt: Instant
)

@Serdeable
@Introspected
data class SuccessResponse(
    val message: String
)

@Serdeable
@Introspected
data class ErrorResponse(
    val error: String,
    val errorCode: String
)
