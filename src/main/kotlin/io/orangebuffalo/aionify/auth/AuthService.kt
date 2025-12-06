package io.orangebuffalo.aionify.auth

import io.orangebuffalo.aionify.domain.UserRepository
import io.quarkus.elytron.security.common.BcryptUtil
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AuthService(
    private val userRepository: UserRepository,
    private val jwtTokenService: JwtTokenService
) {

    fun authenticate(userName: String, password: String): LoginResponse {
        val user = userRepository.findByUserName(userName)
            ?: throw AuthenticationException("Invalid username or password")

        if (!BcryptUtil.matches(password, user.passwordHash)) {
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
            isAdmin = user.isAdmin
        )
    }

    fun changePassword(userName: String, currentPassword: String, newPassword: String) {
        val user = userRepository.findByUserName(userName)
            ?: throw AuthenticationException("User not found")

        if (!BcryptUtil.matches(currentPassword, user.passwordHash)) {
            throw AuthenticationException("Current password is incorrect")
        }

        val newPasswordHash = BcryptUtil.bcryptHash(newPassword)
        userRepository.updatePasswordHash(userName, newPasswordHash)
    }
}
