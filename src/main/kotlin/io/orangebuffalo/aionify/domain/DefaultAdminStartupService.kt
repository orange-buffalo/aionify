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
            locale = Locale.ENGLISH,
            languageCode = "en"
        )

        userRepository.save(defaultAdmin)
        log.warn("Created default admin user 'sudo'. Please check the application output for the generated password.")
        // Print directly to stdout to avoid password appearing in log files
        println("=".repeat(60))
        println("DEFAULT ADMIN CREATED")
        println("Username: sudo")
        println("Password: $randomPassword")
        println("Please change this password after first login!")
        println("=".repeat(60))
        
        return randomPassword
    }

    private fun generateRandomPassword(): String {
        val random = SecureRandom()
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
