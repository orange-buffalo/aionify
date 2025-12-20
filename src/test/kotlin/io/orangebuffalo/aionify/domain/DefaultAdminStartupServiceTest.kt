package io.orangebuffalo.aionify.domain

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestDatabaseSupport
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

@MicronautTest(transactional = false)
class DefaultAdminStartupServiceTest {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var defaultAdminStartupService: DefaultAdminStartupService

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    @BeforeEach
    fun cleanupDatabase() {
        testDatabaseSupport.truncateAllTables()
    }

    @Test
    fun `should create default admin if no admin exists`() {
        // Given: No admin exists (database is truncated)
        assertFalse(userRepository.existsAdmin(), "No admin should exist initially")
        
        // When: Creating default admin
        val password = defaultAdminStartupService.createDefaultAdminIfNeeded()
        
        // Then: Admin should be created with correct values
        assertNotNull(password, "Password should be returned for new admin")
        
        val sudoUser = userRepository.findByUserName("sudo").orElse(null)
        assertNotNull(sudoUser, "Default admin 'sudo' should exist")
        assertEquals("sudo", sudoUser!!.userName)
        assertEquals("Administrator", sudoUser.greeting)
        assertTrue(sudoUser.isAdmin)
        assertEquals(java.util.Locale.ENGLISH.toLanguageTag(), sudoUser.localeTag)
        assertEquals("en", sudoUser.languageCode)
        assertNotNull(sudoUser.passwordHash)
        assertTrue(sudoUser.passwordHash.isNotEmpty())
        
        // Verify the password matches
        assertTrue(BCrypt.checkpw(password, sudoUser.passwordHash), "Password should match the hash")
    }

    @Test
    fun `should not create duplicate admin when admin already exists`() {
        // Given: An admin user already exists
        testDatabaseSupport.insert(
            User.create(
                userName = "existingAdmin",
                passwordHash = BCrypt.hashpw("password", BCrypt.gensalt()),
                greeting = "Existing Admin",
                isAdmin = true,
                locale = java.util.Locale.ENGLISH,
                languageCode = "en"
            )
        )
        assertTrue(userRepository.existsAdmin())
        
        // When: Trying to create default admin again
        val password = defaultAdminStartupService.createDefaultAdminIfNeeded()
        
        // Then: No new admin should be created (null password returned)
        assertNull(password)
        
        // And: The original admin still exists and no 'sudo' user was created
        val sudoUser = userRepository.findByUserName("sudo").orElse(null)
        assertNull(sudoUser, "sudo user should not have been created")
        
        val existingAdmin = userRepository.findByUserName("existingAdmin").orElse(null)
        assertNotNull(existingAdmin, "Original admin should still exist")
    }
}
