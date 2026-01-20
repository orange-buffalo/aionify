package io.orangebuffalo.aionify.auth

import io.orangebuffalo.aionify.domain.UserRepository
import jakarta.inject.Singleton
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory

@Singleton
class AuthService(
    private val userRepository: UserRepository,
    private val jwtTokenService: JwtTokenService,
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    fun authenticate(
        userName: String,
        password: String,
    ): LoginResponse {
        log.debug("Attempting authentication for user: {}", userName)

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Authentication failed: user not found: {}", userName)
            throw AuthenticationException("Invalid username or password")
        }

        if (!BCrypt.checkpw(password, user.passwordHash)) {
            log.debug("Authentication failed: invalid password for user: {}", userName)
            throw AuthenticationException("Invalid username or password")
        }

        log.info("User authenticated successfully: {}", userName)

        val token =
            jwtTokenService.generateToken(
                userName = user.userName,
                userId = requireNotNull(user.id) { "User must have an ID" },
                isAdmin = user.isAdmin,
                greeting = user.greeting,
            )

        return LoginResponse(
            token = token,
            userName = user.userName,
            greeting = user.greeting,
            admin = user.isAdmin,
            languageCode = user.languageCode,
            userId = requireNotNull(user.id) { "User must have an ID" },
        )
    }

    fun changePassword(
        userName: String,
        currentPassword: String,
        newPassword: String,
    ) {
        log.debug("Attempting to change password for user: {}", userName)

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Password change failed: user not found: {}", userName)
            throw AuthenticationException("User not found")
        }

        if (!BCrypt.checkpw(currentPassword, user.passwordHash)) {
            log.debug("Password change failed: incorrect current password for user: {}", userName)
            throw AuthenticationException("Current password is incorrect")
        }

        val newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())
        userRepository.updatePasswordHash(userName, newPasswordHash)

        log.info("Password changed successfully for user: {}", userName)
    }

    fun refreshToken(userName: String): RefreshTokenResponse {
        log.debug("Attempting to refresh token for user: {}", userName)

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Token refresh failed: user not found: {}", userName)
            throw AuthenticationException("User not found")
        }

        log.info("Token refreshed successfully for user: {}", userName)

        val token =
            jwtTokenService.generateToken(
                userName = user.userName,
                userId = requireNotNull(user.id) { "User must have an ID" },
                isAdmin = user.isAdmin,
                greeting = user.greeting,
            )

        return RefreshTokenResponse(token = token)
    }

    fun authenticateById(userId: Long): LoginResponse {
        log.debug("Attempting authentication by user ID: {}", userId)

        val user = userRepository.findById(userId).orElse(null)
        if (user == null) {
            log.debug("Authentication failed: user ID not found: {}", userId)
            throw AuthenticationException("User not found")
        }

        log.info("User authenticated successfully by ID: {}", user.userName)

        val token =
            jwtTokenService.generateToken(
                userName = user.userName,
                userId = requireNotNull(user.id) { "User must have an ID" },
                isAdmin = user.isAdmin,
                greeting = user.greeting,
            )

        return LoginResponse(
            token = token,
            userName = user.userName,
            greeting = user.greeting,
            admin = user.isAdmin,
            languageCode = user.languageCode,
            userId = requireNotNull(user.id) { "User must have an ID" },
        )
    }
}
