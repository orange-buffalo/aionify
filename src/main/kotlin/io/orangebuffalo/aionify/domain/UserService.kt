package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64

@Singleton
class UserService(private val userRepository: UserRepository) {
    
    private val log = LoggerFactory.getLogger(UserService::class.java)
    
    companion object {
        private val secureRandom = SecureRandom()
    }
    
    fun findAllPaginated(page: Int, size: Int): PagedUsers {
        log.debug("Finding users, page: {}, size: {}", page, size)
        val offset = page * size
        val users = userRepository.findAllPaginated(offset, size)
        val total = userRepository.countAll()
        
        log.trace("Found {} users out of {} total", users.size, total)
        
        return PagedUsers(
            users = users,
            total = total,
            page = page,
            size = size
        )
    }
    
    fun updateProfile(userName: String, greeting: String, locale: java.util.Locale) {
        log.info("Updating profile for user: {}, locale: {}", userName, locale)
        userRepository.updateProfile(userName, greeting, locale.toLanguageTag())
    }
    
    /**
     * Generates a secure random password of specified length using Base64 encoding.
     * The password will contain alphanumeric characters and symbols (+, /).
     */
    fun generateRandomPassword(length: Int): String {
        log.trace("Generating random password of length: {}", length)
        // Calculate how many bytes we need to generate a password of the desired length
        // Base64 encoding uses ~4/3 ratio, so we need length * 3 / 4 bytes
        val bytesNeeded = (length * 3) / 4 + 1
        val bytes = ByteArray(bytesNeeded)
        secureRandom.nextBytes(bytes)
        
        // Encode to Base64 and trim to exact length
        return Base64.getEncoder().encodeToString(bytes).take(length)
    }
}
