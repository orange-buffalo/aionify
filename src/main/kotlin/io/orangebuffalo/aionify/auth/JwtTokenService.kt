package io.orangebuffalo.aionify.auth

import io.jsonwebtoken.Jwts
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyPair
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Service for generating and validating JWT tokens.
 * 
 * This service automatically generates an RSA key pair at startup for signing JWTs.
 * The keys are ephemeral - they exist only for the lifetime of the application instance.
 * This means:
 * - Users will need to log in again after application restart
 * - The application is designed for single-instance deployments
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
    
    @Volatile
    private lateinit var publicKeyPath: Path

    fun init(@Observes event: StartupEvent) {
        // Generate a new RSA key pair for signing JWTs at startup
        keyPair = Jwts.SIG.RS256.keyPair().build()
        
        // Write public key to a temporary file for SmallRye JWT to read
        publicKeyPath = Paths.get(System.getProperty("java.io.tmpdir"), "aionify-jwt-public.pem")
        val publicKeyPem = convertPublicKeyToPem(keyPair.public)
        
        // Write with restricted permissions (owner read/write only) if on Unix-like system
        try {
            val perms = PosixFilePermissions.fromString("rw-------")
            val attrs = PosixFilePermissions.asFileAttribute(perms)
            Files.deleteIfExists(publicKeyPath)
            Files.createFile(publicKeyPath, attrs)
            Files.writeString(publicKeyPath, publicKeyPem)
            log.info("JWT public key written to: $publicKeyPath")
        } catch (e: Exception) {
            // Handle all exceptions (UnsupportedOperationException, IOException, etc.)
            log.warn("Could not set POSIX permissions on JWT public key file. Falling back to default permissions.", e)
            try {
                Files.deleteIfExists(publicKeyPath)
                Files.writeString(publicKeyPath, publicKeyPem)
                log.info("JWT public key written to: $publicKeyPath (with default permissions)")
            } catch (e: Exception) {
                log.error("Failed to write JWT public key file", e)
                throw RuntimeException("Failed to initialize JWT token service", e)
            }
        }
    }
    
    fun shutdown(@Observes event: ShutdownEvent) {
        // Clean up the temporary public key file on shutdown
        try {
            if (::publicKeyPath.isInitialized && Files.exists(publicKeyPath)) {
                Files.delete(publicKeyPath)
                log.info("JWT public key file cleaned up: $publicKeyPath")
            }
        } catch (e: Exception) {
            log.warn("Failed to clean up JWT public key file: $publicKeyPath", e)
        }
    }

    private fun convertPublicKeyToPem(publicKey: java.security.PublicKey): String {
        val encoded = Base64.getEncoder().encodeToString(publicKey.encoded)
        return "-----BEGIN PUBLIC KEY-----\n" +
                encoded.chunked(64).joinToString("\n") +
                "\n-----END PUBLIC KEY-----\n"
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
            .claim("upn", userName)  // SmallRye JWT expects 'upn' (User Principal Name) claim
            .claim("preferred_username", userName)  // Alternative claim for username
            .claim("userId", userId)
            .claim("isAdmin", isAdmin)
            .claim("greeting", greeting)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expirationTime))
            .signWith(keyPair.private)
            .compact()
    }

    /**
     * Returns the public key for token validation.
     */
    fun getPublicKey() = keyPair.public
}
