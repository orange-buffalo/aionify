package io.orangebuffalo.aionify.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.security.KeyPair
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Service for generating and validating JWT tokens.
 * 
 * This service automatically generates an RSA key pair at startup for signing and validating JWTs.
 * The keys are ephemeral and kept in-memory only - they exist only for the lifetime of the application instance.
 * This means:
 * - Users will need to log in again after application restart
 * - The application is designed for single-instance deployments
 * - No key storage or management is required
 */
@ApplicationScoped
class JwtTokenService(
    @param:ConfigProperty(name = "aionify.jwt.issuer", defaultValue = "aionify")
    private val jwtIssuer: String,
    @param:ConfigProperty(name = "aionify.jwt.expiration-minutes", defaultValue = "1440")
    private val jwtExpirationMinutes: Long
) {
    private val log = Logger.getLogger(JwtTokenService::class.java)
    
    @Volatile
    private lateinit var keyPair: KeyPair

    fun init(@Observes event: StartupEvent) {
        // Generate a new RSA key pair for signing JWTs at startup
        // Keys are kept in-memory only, no file storage
        keyPair = Jwts.SIG.RS256.keyPair().build()
        log.info("JWT RSA key pair generated and ready for use (in-memory only)")
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
        val now = Instant.now()
        val expirationTime = now.plus(jwtExpirationMinutes, ChronoUnit.MINUTES)

        return Jwts.builder()
            .issuer(jwtIssuer)
            .subject(userName)
            .claim("upn", userName)  // User Principal Name
            .claim("preferred_username", userName)
            .claim("userId", userId)
            .claim("isAdmin", isAdmin)
            .claim("greeting", greeting)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expirationTime))
            .signWith(keyPair.private)
            .compact()
    }

    /**
     * Validates a JWT token and returns its claims.
     * 
     * @throws io.jsonwebtoken.JwtException if the token is invalid
     */
    fun validateToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(keyPair.public)
            .requireIssuer(jwtIssuer)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
