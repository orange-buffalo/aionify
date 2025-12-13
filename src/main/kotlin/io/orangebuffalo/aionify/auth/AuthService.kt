package io.orangebuffalo.aionify.auth

import io.orangebuffalo.aionify.domain.UserRepository
import jakarta.inject.Singleton
import org.mindrot.jbcrypt.BCrypt

@Singleton
class AuthService(
    private val userRepository: UserRepository,
    private val jwtTokenService: JwtTokenService
) {

    fun authenticate(userName: String, password: String): LoginResponse {
        val user = userRepository.findByUserName(userName).orElse(null)
            ?: throw AuthenticationException("Invalid username or password")

        if (!BCrypt.checkpw(password, user.passwordHash)) {
            throw AuthenticationException("Invalid username or password")
        }

        val token = jwtTokenService.generateToken(
            userName = user.userName,
            userId = requireNotNull(user.id) { "User must have an ID" },
            isAdmin = user.isAdmin,
            greeting = user.greeting
        )

        return LoginResponse(
            token = token,
            userName = user.userName,
            greeting = user.greeting,
            admin = user.isAdmin,
            languageCode = user.languageCode
        )
    }

    fun changePassword(userName: String, currentPassword: String, newPassword: String) {
        val user = userRepository.findByUserName(userName).orElse(null)
            ?: throw AuthenticationException("User not found")

        if (!BCrypt.checkpw(currentPassword, user.passwordHash)) {
            throw AuthenticationException("Current password is incorrect")
        }

        val newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())
        userRepository.updatePasswordHash(userName, newPasswordHash)
    }
}
