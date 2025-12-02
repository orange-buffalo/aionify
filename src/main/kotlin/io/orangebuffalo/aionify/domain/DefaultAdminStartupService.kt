package io.orangebuffalo.aionify.domain

import io.quarkus.elytron.security.common.BcryptUtil
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
            passwordHash = BcryptUtil.bcryptHash(randomPassword),
            greeting = "Administrator",
            isAdmin = true,
            locale = Locale.ENGLISH,
            languageCode = "en"
        )

        userRepository.insert(defaultAdmin)
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
