package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import java.security.SecureRandom
import java.util.Base64

@Singleton
class UserService(private val userRepository: UserRepository) {
    
    companion object {
        private val secureRandom = SecureRandom()
    }
    
    fun findAllPaginated(page: Int, size: Int): PagedUsers {
        val offset = page * size
        val users = userRepository.findAllPaginated(offset, size)
        val total = userRepository.countAll()
        
        return PagedUsers(
            users = users,
            total = total,
            page = page,
            size = size
        )
    }
    
    fun updateProfile(userName: String, greeting: String, languageCode: String, locale: java.util.Locale) {
        userRepository.updateProfile(userName, greeting, languageCode, locale.toLanguageTag())
    }
    
    /**
     * Generates a secure random password of specified length using Base64 encoding.
     * The password will contain alphanumeric characters and symbols (+, /).
     */
    fun generateRandomPassword(length: Int): String {
        // Calculate how many bytes we need to generate a password of the desired length
        // Base64 encoding uses ~4/3 ratio, so we need length * 3 / 4 bytes
        val bytesNeeded = (length * 3) / 4 + 1
        val bytes = ByteArray(bytesNeeded)
        secureRandom.nextBytes(bytes)
        
        // Encode to Base64 and trim to exact length
        return Base64.getEncoder().encodeToString(bytes).take(length)
    }
}
