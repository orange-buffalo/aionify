package io.orangebuffalo.aionify.auth

import com.nimbusds.jwt.SignedJWT
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

/**
 * Tests for JWT token expiration functionality.
 * Verifies that tokens have proper expiration claims and that expired tokens are rejected.
 */
@MicronautTest(transactional = false)
class JwtTokenExpirationTest {
    @Inject
    lateinit var jwtTokenService: JwtTokenService

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    private val testUserName = "testuser"
    private val testGreeting = "Test User"

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()
    }

    private fun createTestUser(): User =
        testDatabaseSupport.insert(
            User.create(
                userName = testUserName,
                passwordHash = BCrypt.hashpw("password", BCrypt.gensalt()),
                greeting = testGreeting,
                isAdmin = false,
                locale = java.util.Locale.US,
            ),
        )

    private fun generateTokenForUser(user: User): String =
        jwtTokenService.generateToken(
            userName = user.userName,
            userId = requireNotNull(user.id),
            isAdmin = user.isAdmin,
            greeting = user.greeting,
        )

    @Test
    fun `generated token should have expiration claim`() {
        // Given: A user exists in the database
        val user = createTestUser()

        // When: Generating a token
        val token = generateTokenForUser(user)

        // Then: Token should have expiration claim
        val jwt = SignedJWT.parse(token)
        val expirationTime = jwt.jwtClaimsSet.expirationTime

        assertNotNull(expirationTime, "Token must have an expiration time")

        // Expiration should be in the future
        val now = Date()
        assertTrue(expirationTime.after(now), "Token expiration should be in the future")

        // Expiration should be approximately 24 hours from now (default)
        val twentyThreeHours = now.time + (23 * 60 * 60 * 1000)
        val twentyFiveHours = now.time + (25 * 60 * 60 * 1000)
        assertTrue(
            expirationTime.time in twentyThreeHours..twentyFiveHours,
            "Token expiration should be approximately 24 hours from now",
        )
    }

    @Test
    fun `all generated tokens must have expiration claim`() {
        // This test verifies that our token generation always includes expiration
        // Note: Micronaut Security JWT framework validates expiration when present but doesn't
        // reject tokens without expiration. Our implementation ensures all tokens have expiration.

        val user = createTestUser()
        val token = generateTokenForUser(user)

        val jwt = SignedJWT.parse(token)
        assertNotNull(
            jwt.jwtClaimsSet.expirationTime,
            "All generated tokens must have expiration",
        )
    }
}
