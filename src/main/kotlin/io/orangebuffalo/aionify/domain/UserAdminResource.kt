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
import jakarta.transaction.Transactional
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.security.Principal
import java.time.Instant

@Controller("/api-ui/admin/users")
@Secured("admin")
@Transactional
open class UserAdminResource(
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val userSettingsRepository: UserSettingsRepository,
    private val activationTokenRepository: ActivationTokenRepository,
    private val activationTokenService: ActivationTokenService,
    private val timeService: TimeService,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(UserAdminResource::class.java)

    @Get
    open fun listUsers(
        @QueryValue(defaultValue = "0") page: Int,
        @QueryValue(defaultValue = "20") size: Int,
    ): HttpResponse<*> {
        if (page < 0) {
            log.debug("Invalid page parameter: {}", page)
            return HttpResponse.badRequest(ErrorResponse("Page must be non-negative", "INVALID_PAGE"))
        }

        if (size <= 0 || size > 100) {
            log.debug("Invalid size parameter: {}", size)
            return HttpResponse.badRequest(ErrorResponse("Size must be between 1 and 100", "INVALID_SIZE"))
        }

        val pagedUsers = userService.findAllPaginated(page, size)

        return HttpResponse.ok(
            UsersListResponse(
                users =
                    pagedUsers.users.map { user ->
                        UserDto(
                            id = requireNotNull(user.id) { "User must have an ID" },
                            userName = user.userName,
                            greeting = user.greeting,
                            isAdmin = user.isAdmin,
                        )
                    },
                total = pagedUsers.total,
                page = pagedUsers.page,
                size = pagedUsers.size,
            ),
        )
    }

    @Post
    open fun createUser(
        @Valid @Body request: CreateUserRequest,
    ): HttpResponse<*> {
        log.debug("Creating user: {}", request.userName)

        // Check if username already exists
        val existingUser = userRepository.findByUserName(request.userName).orElse(null)
        if (existingUser != null) {
            log.debug("User creation failed: username already exists: {}", request.userName)
            return HttpResponse.badRequest(ErrorResponse("Username already exists", "USERNAME_ALREADY_EXISTS"))
        }

        // Generate a long random password (100+ characters)
        val randomPassword = userService.generateRandomPassword(100)

        // Create the user with default English (US) locale
        val user =
            userRepository.save(
                User.create(
                    userName = request.userName,
                    passwordHash =
                        org.mindrot.jbcrypt.BCrypt
                            .hashpw(
                                randomPassword,
                                org.mindrot.jbcrypt.BCrypt
                                    .gensalt(),
                            ),
                    greeting = request.greeting,
                    isAdmin = request.isAdmin,
                    locale = java.util.Locale.US,
                ),
            )

        log.info("User created: {}, isAdmin: {}", request.userName, request.isAdmin)

        // Create default user settings
        userSettingsRepository.save(UserSettings.create(userId = requireNotNull(user.id)))

        // Create activation token with 10 days (240 hours) expiration
        val activationToken = activationTokenService.createToken(requireNotNull(user.id))

        return HttpResponse.created(
            UserCreatedResponse(
                id = requireNotNull(user.id),
                userName = user.userName,
                greeting = user.greeting,
                isAdmin = user.isAdmin,
                activationToken =
                    ActivationTokenInfo(
                        token = activationToken.token,
                        expiresAt = activationToken.expiresAt,
                    ),
            ),
        )
    }

    @Get("/{id}")
    open fun getUser(
        @PathVariable id: Long,
    ): HttpResponse<*> {
        log.debug("Getting user by id: {}", id)

        val user = userRepository.findById(id).orElse(null)
        if (user == null) {
            log.debug("User not found: {}", id)
            return HttpResponse
                .notFound<ErrorResponse>()
                .body(ErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        // Check for activation token
        val activationToken = activationTokenRepository.findByUserId(id).orElse(null)
        val activationTokenInfo =
            if (activationToken != null && activationToken.expiresAt.isAfter(timeService.now())) {
                log.trace("Active activation token found for user: {}", id)
                ActivationTokenInfo(
                    token = activationToken.token,
                    expiresAt = activationToken.expiresAt,
                )
            } else {
                if (activationToken != null) {
                    log.trace("Expired activation token found for user: {}", id)
                }
                null
            }

        return HttpResponse.ok(
            UserDetailDto(
                id = requireNotNull(user.id) { "User must have an ID" },
                userName = user.userName,
                greeting = user.greeting,
                isAdmin = user.isAdmin,
                activationToken = activationTokenInfo,
            ),
        )
    }

    @Put("/{id}")
    open fun updateUser(
        @PathVariable id: Long,
        @Valid @Body request: UpdateUserRequest,
    ): HttpResponse<*> {
        log.debug("Updating user: {}", id)

        val user = userRepository.findById(id).orElse(null)
        if (user == null) {
            log.debug("Update failed: user not found: {}", id)
            return HttpResponse
                .notFound<ErrorResponse>()
                .body(ErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        // Check if username is being changed
        if (request.userName != user.userName) {
            log.debug("Username change requested for user {}: {} -> {}", id, user.userName, request.userName)

            // Check for username uniqueness
            val existingUser = userRepository.findByUserName(request.userName).orElse(null)
            if (existingUser != null) {
                log.debug("Username change failed: username already exists: {}", request.userName)
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
                ),
            )

            log.info("User updated: {}, new username: {}", id, request.userName)
        }

        return HttpResponse.ok(SuccessResponse("User updated successfully"))
    }

    @Post("/{id}/regenerate-activation-token")
    open fun regenerateActivationToken(
        @PathVariable id: Long,
    ): HttpResponse<*> {
        log.debug("Regenerating activation token for user: {}", id)

        val user = userRepository.findById(id).orElse(null)
        if (user == null) {
            log.debug("Token regeneration failed: user not found: {}", id)
            return HttpResponse
                .notFound<ErrorResponse>()
                .body(ErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        // Generate new activation token
        val activationToken = activationTokenService.createToken(requireNotNull(user.id))

        log.info("Activation token regenerated for user: {}", id)

        return HttpResponse.ok(
            ActivationTokenResponse(
                token = activationToken.token,
                expiresAt = activationToken.expiresAt,
            ),
        )
    }

    @Delete("/{id}")
    open fun deleteUser(
        @PathVariable id: Long,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        // Prevent self-deletion
        if (currentUser.id == id) {
            log.debug("User deletion failed: attempt to delete self by user: {}", currentUser.user.userName)
            return HttpResponse.badRequest(ErrorResponse("Cannot delete your own user account", "CANNOT_DELETE_SELF"))
        }

        val deleted = userRepository.existsById(id)
        if (deleted) {
            userRepository.deleteById(id)
            log.info("User deleted: {}", id)
        } else {
            log.debug("User deletion failed: user not found: {}", id)
        }

        return if (deleted) {
            HttpResponse.ok(SuccessResponse("User deleted successfully"))
        } else {
            HttpResponse
                .notFound<ErrorResponse>()
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
    val isAdmin: Boolean,
)

@Serdeable
@Introspected
data class UsersListResponse(
    val users: List<UserDto>,
    val total: Long,
    val page: Int,
    val size: Int,
)

@Serdeable
@Introspected
data class UserDetailDto(
    val id: Long,
    val userName: String,
    val greeting: String,
    val isAdmin: Boolean,
    val activationToken: ActivationTokenInfo?,
)

@Serdeable
@Introspected
data class ActivationTokenInfo(
    val token: String,
    val expiresAt: Instant,
)

@Serdeable
@Introspected
data class UpdateUserRequest(
    @field:NotBlank(message = "Username cannot be blank")
    @field:Size(max = 255, message = "Username cannot exceed 255 characters")
    val userName: String,
)

@Serdeable
@Introspected
data class ActivationTokenResponse(
    val token: String,
    val expiresAt: Instant,
)

@Serdeable
@Introspected
data class SuccessResponse(
    val message: String,
)

@Serdeable
@Introspected
data class CreateUserRequest(
    @field:NotBlank(message = "Username cannot be blank")
    @field:Size(max = 255, message = "Username cannot exceed 255 characters")
    val userName: String,
    @field:NotBlank(message = "Greeting cannot be blank")
    @field:Size(max = 255, message = "Greeting cannot exceed 255 characters")
    val greeting: String,
    val isAdmin: Boolean,
)

@Serdeable
@Introspected
data class UserCreatedResponse(
    val id: Long,
    val userName: String,
    val greeting: String,
    val isAdmin: Boolean,
    val activationToken: ActivationTokenInfo,
)

@Serdeable
@Introspected
data class ErrorResponse(
    val error: String,
    val errorCode: String,
)
