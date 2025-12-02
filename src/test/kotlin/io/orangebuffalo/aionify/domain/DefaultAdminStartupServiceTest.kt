package io.orangebuffalo.aionify.domain

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Locale

@QuarkusTest
class DefaultAdminStartupServiceTest {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var defaultAdminStartupService: DefaultAdminStartupService

    @Test
    fun `should create default admin if no admin exists`() {
        // Given: No admin exists (verified by the service startup)
        // The default admin should have been created during startup
        
        val sudoUser = userRepository.findByUserName("sudo")
        
        assertNotNull(sudoUser, "Default admin 'sudo' should exist")
        assertEquals("sudo", sudoUser!!.userName)
        assertEquals("Administrator", sudoUser.greeting)
        assertTrue(sudoUser.isAdmin)
        assertEquals(Locale.ENGLISH, sudoUser.locale)
        assertEquals("en", sudoUser.languageCode)
        assertNotNull(sudoUser.passwordHash)
        assertTrue(sudoUser.passwordHash.isNotEmpty())
    }

    @Test
    fun `should not create duplicate admin when admin already exists`() {
        // Given: Admin already exists from startup
        assertTrue(userRepository.existsAdmin())
        
        // When: Trying to create default admin again
        val password = defaultAdminStartupService.createDefaultAdminIfNeeded()
        
        // Then: No new admin should be created (null password returned)
        assertNull(password)
        
        // And: Still only one admin with username 'sudo'
        val sudoUser = userRepository.findByUserName("sudo")
        assertNotNull(sudoUser)
    }
}
