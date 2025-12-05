package io.orangebuffalo.aionify.auth

import io.orangebuffalo.aionify.domain.UserRepository
import io.quarkus.elytron.security.common.BcryptUtil
import io.smallrye.jwt.build.Jwt
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration

@ApplicationScoped
class AuthService(
    private val userRepository: UserRepository,
    @param:ConfigProperty(name = "aionify.jwt.issuer", defaultValue = "aionify")
    private val jwtIssuer: String,
    @param:ConfigProperty(name = "aionify.jwt.expiration-minutes", defaultValue = "1440")
    private val jwtExpirationMinutes: Long
) {

    fun authenticate(userName: String, password: String): LoginResponse {
        val user = userRepository.findByUserName(userName)
            ?: throw AuthenticationException("Invalid username or password")

        if (!BcryptUtil.matches(password, user.passwordHash)) {
            throw AuthenticationException("Invalid username or password")
        }

        val token = Jwt.issuer(jwtIssuer)
            .subject(user.userName)
            .claim("userId", user.id)
            .claim("isAdmin", user.isAdmin)
            .claim("greeting", user.greeting)
            .expiresIn(Duration.ofMinutes(jwtExpirationMinutes))
            .sign()

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
