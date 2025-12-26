package io.orangebuffalo.aionify.domain

import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.event.ApplicationStartupEvent
import jakarta.inject.Singleton
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

@Singleton
class DefaultAdminStartupService(
    private val userRepository: UserRepository
) : ApplicationEventListener<ApplicationStartupEvent> {

    private val log = LoggerFactory.getLogger(DefaultAdminStartupService::class.java)

    override fun onApplicationEvent(event: ApplicationStartupEvent) {
        createDefaultAdminIfNeeded()
    }

    internal fun createDefaultAdminIfNeeded(): String? {
        if (userRepository.existsAdmin()) {
            log.info("Admin user already exists, skipping default admin creation")
            return null
        }

        val randomPassword = generateRandomPassword()
        val defaultAdmin = User.create(
            userName = "sudo",
            passwordHash = BCrypt.hashpw(randomPassword, BCrypt.gensalt()),
            greeting = "Administrator",
            isAdmin = true,
            locale = Locale.US
        )

        userRepository.save(defaultAdmin)
        
        // Log admin creation with password to console for first-time setup
        // WARNING: This contains sensitive information and should only be logged to console during initial setup
        val adminCreationMessage = buildString {
            appendLine("=".repeat(60))
            appendLine("DEFAULT ADMIN CREATED")
            appendLine("Username: sudo")
            appendLine("Password: $randomPassword")
            appendLine("Please change this password after first login!")
            appendLine("=".repeat(60))
        }
        log.warn(adminCreationMessage)
        
        return randomPassword
    }

    private fun generateRandomPassword(): String {
        val random = SecureRandom()
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
