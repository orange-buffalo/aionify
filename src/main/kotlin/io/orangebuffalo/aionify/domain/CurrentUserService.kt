package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import java.security.Principal

/**
 * Service for resolving the current authenticated user from a Principal.
 */
@Singleton
class CurrentUserService(
    private val userRepository: UserRepository,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(CurrentUserService::class.java)

    /**
     * Resolves the current user from the provided principal.
     *
     * @param principal The security principal, may be null
     * @return UserWithId containing the user and their ID
     * @throws UserNotAuthenticatedException if principal is null
     * @throws UserNotFoundException if user is not found in database
     */
    fun resolveUser(principal: Principal?): UserWithId {
        val userName = principal?.name
        if (userName == null) {
            log.debug("User resolution failed: user not authenticated")
            throw UserNotAuthenticatedException()
        }

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("User resolution failed: user not found: {}", userName)
            throw UserNotFoundException(userName)
        }

        val userId = requireNotNull(user.id) { "User must have an ID" }
        return UserWithId(user = user, id = userId)
    }
}
