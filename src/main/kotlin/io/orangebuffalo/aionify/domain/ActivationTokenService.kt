package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Singleton
class ActivationTokenService(
    private val activationTokenRepository: ActivationTokenRepository,
    private val userRepository: UserRepository
) {
    
    companion object {
        private const val TOKEN_LENGTH = 32
        private val secureRandom = SecureRandom()
    }
    
    /**
     * Creates a new activation token for the given user.
     * Expires in 10 days (240 hours) by default.
     */
    fun createToken(userId: Long, expirationHours: Long = 240): ActivationToken {
        val token = generateSecureToken()
        val expiresAt = Instant.now().plus(expirationHours, ChronoUnit.HOURS)
        
        // Delete any existing tokens for this user
        activationTokenRepository.deleteByUserId(userId)
        
        return activationTokenRepository.save(
            ActivationToken(
                userId = userId,
                token = token,
                expiresAt = expiresAt
            )
        )
    }
    
    /**
     * Validates an activation token and returns the associated user if valid.
     * Returns null if token is invalid or expired.
     */
    fun validateToken(token: String): User? {
        val activationToken = activationTokenRepository.findByToken(token).orElse(null)
            ?: return null
        
        // Check if token is expired
        if (activationToken.expiresAt.isBefore(Instant.now())) {
            return null
        }
        
        // Return the associated user
        return userRepository.findById(activationToken.userId).orElse(null)
    }
    
    /**
     * Sets the password for a user using an activation token.
     * Returns true if successful, false if token is invalid or expired.
     */
    fun setPasswordWithToken(token: String, newPassword: String): Boolean {
        val activationToken = activationTokenRepository.findByToken(token).orElse(null)
            ?: return false
        
        // Check if token is expired
        if (activationToken.expiresAt.isBefore(Instant.now())) {
            return false
        }
        
        // Get the user
        val user = userRepository.findById(activationToken.userId).orElse(null)
            ?: return false
        
        // Update password
        val newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())
        userRepository.updatePasswordHash(user.userName, newPasswordHash)
        
        // Delete the used token
        activationTokenRepository.deleteByUserId(activationToken.userId)
        
        return true
    }
    
    /**
     * Cleans up expired tokens from the database.
     */
    fun cleanupExpiredTokens() {
        activationTokenRepository.deleteExpiredTokens(Instant.now())
    }
    
    private fun generateSecureToken(): String {
        val bytes = ByteArray(TOKEN_LENGTH)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
