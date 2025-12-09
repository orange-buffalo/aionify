package io.orangebuffalo.aionify.auth

import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.generator.TokenGenerator
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

/**
 * Service for generating JWT tokens using Micronaut Security JWT.
 * 
 * Token generation is handled by Micronaut Security JWT library.
 * The signing key is configured in application.yml (JWT_SECRET environment variable or default).
 */
@Singleton
class JwtTokenService(
    private val tokenGenerator: TokenGenerator
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
        val roles = if (isAdmin) listOf("admin", "user") else listOf("user")
        
        val attributes = mapOf(
            "userId" to userId,
            "greeting" to greeting,
            "isAdmin" to isAdmin
        )
        
        val authentication = Authentication.build(userName, roles, attributes)
        
        val token = tokenGenerator.generateToken(authentication, null)
        return token.orElseThrow { RuntimeException("Failed to generate JWT token") }
    }
}
