package io.orangebuffalo.aionify.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * Service for generating and validating JWT tokens using Auth0 java-jwt library.
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
    private lateinit var algorithm: Algorithm

    fun init(@Observes event: StartupEvent) {
        // Generate a new RSA key pair for signing JWTs at startup
        // Keys are kept in-memory only, no file storage
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair: KeyPair = keyPairGenerator.generateKeyPair()
        
        algorithm = Algorithm.RSA256(
            keyPair.public as RSAPublicKey,
            keyPair.private as RSAPrivateKey
        )
        
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

        return JWT.create()
            .withIssuer(jwtIssuer)
            .withSubject(userName)
            .withClaim("upn", userName)  // User Principal Name
            .withClaim("preferred_username", userName)
            .withClaim("userId", userId)
            .withClaim("isAdmin", isAdmin)
            .withClaim("greeting", greeting)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expirationTime))
            .sign(algorithm)
    }

    /**
     * Validates a JWT token and returns the decoded JWT.
     * 
     * @throws com.auth0.jwt.exceptions.JWTVerificationException if the token is invalid
     */
    fun validateToken(token: String): DecodedJWT {
        val verifier = JWT.require(algorithm)
            .withIssuer(jwtIssuer)
            .build()
        
        return verifier.verify(token)
    }
}
