package io.orangebuffalo.aionify.auth

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.domain.RememberMeToken
import io.orangebuffalo.aionify.domain.RememberMeTokenRepository
import io.orangebuffalo.aionify.domain.TimeService
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt
import java.time.Duration

/**
 * Unit tests for RememberMeService.
 */
@MicronautTest(transactional = false)
class RememberMeServiceTest {
    @Inject
    lateinit var rememberMeService: RememberMeService

    @Inject
    lateinit var rememberMeTokenRepository: RememberMeTokenRepository

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    @Inject
    lateinit var timeService: TimeService

    private lateinit var testUser: User

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()

        testUser =
            testDatabaseSupport.insert(
                User.create(
                    userName = "testuser",
                    passwordHash = BCrypt.hashpw("password", BCrypt.gensalt()),
                    greeting = "Test User",
                    isAdmin = false,
                    locale = java.util.Locale.US,
                ),
            )
    }

    @Test
    fun `should generate unique token with hash`() {
        // Given
        val userId = requireNotNull(testUser.id) { "User must have an ID" }
        val userAgent = "Mozilla/5.0 Test Browser"

        // When
        val (token, savedToken) = rememberMeService.generateToken(userId, userAgent)

        // Then
        assertNotNull(token, "Token should not be null")
        assertTrue(token.isNotEmpty(), "Token should not be empty")
        assertNotNull(savedToken.id, "Saved token should have an ID")
        assertEquals(userId, savedToken.userId, "Token should be associated with the user")
        assertEquals(userAgent, savedToken.userAgent, "User agent should be stored")

        // Token should be base64-encoded (URL-safe)
        assertTrue(token.matches(Regex("^[A-Za-z0-9_-]+$")), "Token should be base64 URL-safe encoded")

        // Token hash should be different from token (it's hashed)
        assertNotEquals(token, savedToken.tokenHash, "Token hash should be different from plain token")
    }

    @Test
    fun `should generate different tokens on each call`() {
        // Given
        val userId = requireNotNull(testUser.id) { "User must have an ID" }

        // When
        val (token1, _) = rememberMeService.generateToken(userId, "Agent1")
        val (token2, _) = rememberMeService.generateToken(userId, "Agent2")

        // Then
        assertNotEquals(token1, token2, "Each generated token should be unique")
    }

    @Test
    fun `should set token expiration to 30 days by default`() {
        // Given
        val userId = requireNotNull(testUser.id) { "User must have an ID" }
        val now = timeService.now()

        // When
        val (_, savedToken) = rememberMeService.generateToken(userId, null)

        // Then
        val expectedExpiration = now.plus(Duration.ofDays(30))
        assertEquals(expectedExpiration, savedToken.expiresAt, "Token should expire in 30 days")
    }

    @Test
    fun `should validate valid token`() {
        // Given
        val userId = requireNotNull(testUser.id) { "User must have an ID" }
        val userAgent = "Test Browser"
        val (token, _) = rememberMeService.generateToken(userId, userAgent)

        // When
        val validatedUserId = rememberMeService.validateToken(token, userAgent)

        // Then
        assertNotNull(validatedUserId, "Token validation should succeed")
        assertEquals(userId, validatedUserId, "Validated user ID should match")
    }

    @Test
    fun `should reject invalid token`() {
        // Given
        val invalidToken = "InvalidTokenThatDoesNotExist"

        // When
        val validatedUserId = rememberMeService.validateToken(invalidToken, "Any Agent")

        // Then
        assertNull(validatedUserId, "Invalid token should not be validated")
    }

    @Test
    fun `should reject expired token and delete it`() {
        // Given
        val userId = requireNotNull(testUser.id) { "User must have an ID" }
        val (token, savedToken) = rememberMeService.generateToken(userId, "Test Browser")

        // Manually expire the token
        val expiredToken = savedToken.copy(expiresAt = timeService.now().minusSeconds(1))
        rememberMeTokenRepository.update(expiredToken)

        // When
        val validatedUserId = rememberMeService.validateToken(token, "Test Browser")

        // Then
        assertNull(validatedUserId, "Expired token should not be validated")

        // Verify token was deleted
        val tokenExists = rememberMeTokenRepository.findById(savedToken.id!!).isPresent
        assertFalse(tokenExists, "Expired token should be deleted after validation")
    }

    @Test
    fun `should invalidate specific token`() {
        // Given
        val userId = requireNotNull(testUser.id) { "User must have an ID" }
        val (token, savedToken) = rememberMeService.generateToken(userId, "Test Browser")

        // When
        rememberMeService.invalidateToken(token)

        // Then
        val tokenExists = rememberMeTokenRepository.findById(savedToken.id!!).isPresent
        assertFalse(tokenExists, "Token should be deleted after invalidation")

        // Subsequent validation should fail
        val validatedUserId = rememberMeService.validateToken(token, "Test Browser")
        assertNull(validatedUserId, "Invalidated token should not validate")
    }

    @Test
    fun `should invalidate all tokens for user`() {
        // Given
        val userId = requireNotNull(testUser.id) { "User must have an ID" }
        val (token1, _) = rememberMeService.generateToken(userId, "Browser1")
        val (token2, _) = rememberMeService.generateToken(userId, "Browser2")
        val (token3, _) = rememberMeService.generateToken(userId, "Browser3")

        // Verify all tokens exist
        assertEquals(3, rememberMeTokenRepository.count(), "All tokens should exist")

        // When
        rememberMeService.invalidateAllTokensForUser(userId)

        // Then
        assertEquals(0, rememberMeTokenRepository.count(), "All tokens should be deleted")

        // Verify none of the tokens validate
        assertNull(rememberMeService.validateToken(token1, "Browser1"))
        assertNull(rememberMeService.validateToken(token2, "Browser2"))
        assertNull(rememberMeService.validateToken(token3, "Browser3"))
    }

    @Test
    fun `should cleanup expired tokens`() {
        // Given
        val userId = requireNotNull(testUser.id) { "User must have an ID" }
        val now = timeService.now()

        // Create valid token
        val (validToken, _) = rememberMeService.generateToken(userId, "Valid")

        // Create expired tokens manually
        val expiredToken1 =
            RememberMeToken(
                userId = userId,
                tokenHash = "expired1",
                createdAt = now.minus(Duration.ofDays(31)),
                expiresAt = now.minus(Duration.ofDays(1)),
                userAgent = "Expired1",
            )
        val expiredToken2 =
            RememberMeToken(
                userId = userId,
                tokenHash = "expired2",
                createdAt = now.minus(Duration.ofDays(32)),
                expiresAt = now.minus(Duration.ofDays(2)),
                userAgent = "Expired2",
            )

        rememberMeTokenRepository.save(expiredToken1)
        rememberMeTokenRepository.save(expiredToken2)

        // Verify all tokens exist
        assertEquals(3, rememberMeTokenRepository.count(), "All tokens should exist before cleanup")

        // When
        rememberMeService.cleanupExpiredTokens()

        // Then
        assertEquals(1, rememberMeTokenRepository.count(), "Only valid token should remain")

        // Verify valid token still validates
        val validatedUserId = rememberMeService.validateToken(validToken, "Valid")
        assertEquals(userId, validatedUserId, "Valid token should still work")
    }

    @Test
    fun `should truncate long user agent strings`() {
        // Given
        val userId = requireNotNull(testUser.id) { "User must have an ID" }
        val longUserAgent = "A".repeat(600) // Longer than 500 character limit

        // When
        val (_, savedToken) = rememberMeService.generateToken(userId, longUserAgent)

        // Then
        assertNotNull(savedToken.userAgent, "User agent should be stored")
        assertEquals(500, savedToken.userAgent!!.length, "User agent should be truncated to 500 characters")
    }

    @Test
    fun `should handle null user agent`() {
        // Given
        val userId = requireNotNull(testUser.id) { "User must have an ID" }

        // When
        val (token, savedToken) = rememberMeService.generateToken(userId, null)

        // Then
        assertNull(savedToken.userAgent, "User agent should be null")

        // Validation should still work
        val validatedUserId = rememberMeService.validateToken(token, null)
        assertEquals(userId, validatedUserId, "Token should validate with null user agent")
    }
}
