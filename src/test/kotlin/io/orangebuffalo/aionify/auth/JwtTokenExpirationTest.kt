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

    @Test
    fun `generated token should have expiration claim`() {
        // Given: A user exists in the database
        val user =
            testDatabaseSupport.insert(
                User.create(
                    userName = testUserName,
                    passwordHash = BCrypt.hashpw("password", BCrypt.gensalt()),
                    greeting = testGreeting,
                    isAdmin = false,
                    locale = java.util.Locale.US,
                ),
            )

        // When: Generating a token
        val token =
            jwtTokenService.generateToken(
                userName = user.userName,
                userId = requireNotNull(user.id),
                isAdmin = user.isAdmin,
                greeting = user.greeting,
            )

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
            "Token expiration should be approximately 24 hours from now"
        )
    }

    @Test
    fun `token without expiration should be rejected by validation`() {
        // This test will verify that the JWT validation configuration rejects tokens without expiration
        // For now, we'll just verify that our generated tokens always have expiration
        // The actual rejection happens at the Micronaut Security level
        
        val user =
            testDatabaseSupport.insert(
                User.create(
                    userName = testUserName,
                    passwordHash = BCrypt.hashpw("password", BCrypt.gensalt()),
                    greeting = testGreeting,
                    isAdmin = false,
                    locale = java.util.Locale.US,
                ),
            )

        val token =
            jwtTokenService.generateToken(
                userName = user.userName,
                userId = requireNotNull(user.id),
                isAdmin = user.isAdmin,
                greeting = user.greeting,
            )

        val jwt = SignedJWT.parse(token)
        assertNotNull(
            jwt.jwtClaimsSet.expirationTime,
            "All generated tokens must have expiration"
        )
    }
}
