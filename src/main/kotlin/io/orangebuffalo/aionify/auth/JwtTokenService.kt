package io.orangebuffalo.aionify.auth

import com.nimbusds.jwt.JWTClaimsSet
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.security.token.jwt.validator.JwtTokenValidator
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Service for generating and validating JWT tokens using Micronaut Security JWT.
 * 
 * Token generation and validation is handled by Micronaut Security JWT library.
 * The signing key is configured in application.yml (JWT_SECRET environment variable or default).
 */
@Singleton
class JwtTokenService(
    private val jwtTokenGenerator: JwtTokenGenerator,
    private val jwtTokenValidators: Collection<JwtTokenValidator<*>>
) : ApplicationEventListener<ApplicationStartupEvent> {
    
    private val log = LoggerFactory.getLogger(JwtTokenService::class.java)
    
    override fun onApplicationEvent(event: ApplicationStartupEvent) {
        log.info("JWT token service initialized with Micronaut Security")
    }

    /**
     * Generates a JWT token for the given user.
     */
    fun generateToken(
        userName: String,
        userId: Long,
        isAdmin: Boolean,
        greeting: String
    ): String {
        val claims = mapOf(
            "sub" to userName,
            "upn" to userName,
            "preferred_username" to userName,
            "userId" to userId,
            "isAdmin" to isAdmin,
            "greeting" to greeting
        )
        
        val token = jwtTokenGenerator.generateToken(claims)
        return token.orElseThrow { RuntimeException("Failed to generate JWT token") }
    }

    /**
     * Validates a JWT token and returns the claims.
     * 
     * @throws Exception if the token is invalid
     */
    fun validateToken(token: String): JWTClaimsSet {
        for (validator in jwtTokenValidators) {
            val result = validator.validateToken(token, null)
            if (result.isPresent) {
                return result.get() as JWTClaimsSet
            }
        }
        throw RuntimeException("Invalid JWT token")
    }
}
