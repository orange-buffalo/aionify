package io.orangebuffalo.aionify.domain

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.jboss.logging.Logger
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

@ApplicationScoped
class DefaultAdminStartupService(private val userRepository: UserRepository) {

    private val log = Logger.getLogger(DefaultAdminStartupService::class.java)

    fun onStart(@Observes event: StartupEvent) {
        createDefaultAdminIfNeeded()
    }

    internal fun createDefaultAdminIfNeeded(): String? {
        if (userRepository.existsAdmin()) {
            log.info("Admin user already exists, skipping default admin creation")
            return null
        }

        val randomPassword = generateRandomPassword()
        val defaultAdmin = User(
            userName = "sudo",
            passwordHash = hashPassword(randomPassword),
            greeting = "Administrator",
            isAdmin = true,
            locale = Locale.ENGLISH,
            languageCode = "en"
        )

        userRepository.insert(defaultAdmin)
        log.info("Created default admin user 'sudo' with generated password: $randomPassword")
        
        return randomPassword
    }

    private fun generateRandomPassword(): String {
        val random = SecureRandom()
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashPassword(password: String): String {
        // Using a simple hash for now - in production, use BCrypt or similar
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}
