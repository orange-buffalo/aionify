package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Singleton
class ActivationTokenService(
    private val activationTokenRepository: ActivationTokenRepository,
    private val userRepository: UserRepository,
    private val timeService: TimeService
) {
    
    private val log = LoggerFactory.getLogger(ActivationTokenService::class.java)
    
    companion object {
        private const val TOKEN_LENGTH = 32
        private val secureRandom = SecureRandom()
    }
    
    /**
     * Creates a new activation token for the given user.
     * Expires in 10 days (240 hours) by default.
     */
    fun createToken(userId: Long, expirationHours: Long = 240): ActivationToken {
        log.debug("Creating activation token for user: {}, expiration hours: {}", userId, expirationHours)
        
        val token = generateSecureToken()
        val expiresAt = timeService.now().plus(expirationHours, ChronoUnit.HOURS)
        
        // Delete any existing tokens for this user
        val existingTokens = activationTokenRepository.deleteByUserId(userId)
        if (existingTokens > 0) {
            log.trace("Deleted {} existing activation tokens for user: {}", existingTokens, userId)
        }
        
        val savedToken = activationTokenRepository.save(
            ActivationToken(
                userId = userId,
                token = token,
                expiresAt = expiresAt,
                createdAt = timeService.now()
            )
        )
        
        log.info("Activation token created for user: {}, expires at: {}", userId, expiresAt)
        
        return savedToken
    }
    
    /**
     * Validates an activation token and returns the associated user if valid.
     * Returns null if token is invalid or expired.
     */
    fun validateToken(token: String): User? {
        log.debug("Validating activation token")
        
        val activationToken = activationTokenRepository.findByToken(token).orElse(null)
        if (activationToken == null) {
            log.debug("Token validation failed: token not found")
            return null
        }
        
        // Check if token is expired
        if (activationToken.expiresAt.isBefore(timeService.now())) {
            log.debug("Token validation failed: token expired for user: {}", activationToken.userId)
            return null
        }
        
        // Return the associated user
        val user = userRepository.findById(activationToken.userId).orElse(null)
        if (user != null) {
            log.debug("Token validated successfully for user: {}", user.userName)
        } else {
            log.debug("Token validation failed: user not found for token")
        }
        
        return user
    }
    
    /**
     * Sets the password for a user using an activation token.
     * Returns true if successful, false if token is invalid or expired.
     */
    fun setPasswordWithToken(token: String, newPassword: String): Boolean {
        log.debug("Setting password with activation token")
        
        val activationToken = activationTokenRepository.findByToken(token).orElse(null)
        if (activationToken == null) {
            log.debug("Set password failed: token not found")
            return false
        }
        
        // Check if token is expired
        if (activationToken.expiresAt.isBefore(timeService.now())) {
            log.debug("Set password failed: token expired for user: {}", activationToken.userId)
            return false
        }
        
        // Get the user
        val user = userRepository.findById(activationToken.userId).orElse(null)
        if (user == null) {
            log.debug("Set password failed: user not found for token")
            return false
        }
        
        // Update password
        val newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())
        userRepository.updatePasswordHash(user.userName, newPasswordHash)
        
        // Delete the used token
        activationTokenRepository.deleteByUserId(activationToken.userId)
        
        log.info("Password set successfully for user: {} using activation token", user.userName)
        
        return true
    }
    
    /**
     * Cleans up expired tokens from the database.
     */
    fun cleanupExpiredTokens() {
        log.debug("Cleaning up expired activation tokens")
        
        val deletedCount = activationTokenRepository.deleteExpiredTokens(timeService.now())
        
        if (deletedCount > 0) {
            log.info("Cleaned up {} expired activation tokens", deletedCount)
        } else {
            log.trace("No expired activation tokens to clean up")
        }
    }
    
    private fun generateSecureToken(): String {
        val bytes = ByteArray(TOKEN_LENGTH)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
