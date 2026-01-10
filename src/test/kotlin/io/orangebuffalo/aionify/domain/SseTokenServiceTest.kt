package io.orangebuffalo.aionify.domain

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestTimeService
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration

@MicronautTest(transactional = false)
class SseTokenServiceTest {
    @Inject
    lateinit var sseTokenService: SseTokenService

    @Inject
    lateinit var testTimeService: TestTimeService

    @AfterEach
    fun resetTime() { }

    @Test
    fun `should generate unique tokens`() {
        val token1 = sseTokenService.generateToken(1L)
        val token2 = sseTokenService.generateToken(1L)

        assertNotEquals(token1, token2, "Each token should be unique")
        assertTrue(token1.isNotBlank(), "Token should not be blank")
        assertTrue(token2.isNotBlank(), "Token should not be blank")
    }

    @Test
    fun `should validate valid token and return user id`() {
        val userId = 123L
        val token = sseTokenService.generateToken(userId)

        val validatedUserId = sseTokenService.validateToken(token)

        assertEquals(userId, validatedUserId, "Should return the correct user ID")
    }

    @Test
    fun `should return null for invalid token`() {
        val validatedUserId = sseTokenService.validateToken("invalid-token")

        assertNull(validatedUserId, "Should return null for invalid token")
    }

    @Test
    fun `should return null for expired token`() {
        val userId = 456L
        val baseTime = testTimeService.now()
        val token = sseTokenService.generateToken(userId)

        // Advance time by 31 seconds to expire the token (TTL is 30 seconds)
        testTimeService.setTime(baseTime.plusSeconds(31))

        val validatedUserId = sseTokenService.validateToken(token)

        assertNull(validatedUserId, "Should return null for expired token")
    }

    @Test
    fun `should handle multiple users with different tokens`() {
        val user1Id = 100L
        val user2Id = 200L

        val token1 = sseTokenService.generateToken(user1Id)
        val token2 = sseTokenService.generateToken(user2Id)

        val validated1 = sseTokenService.validateToken(token1)
        val validated2 = sseTokenService.validateToken(token2)

        assertEquals(user1Id, validated1, "Token 1 should validate to user 1")
        assertEquals(user2Id, validated2, "Token 2 should validate to user 2")
    }

    @Test
    fun `should allow same user to have multiple active tokens`() {
        val userId = 789L

        val token1 = sseTokenService.generateToken(userId)
        val token2 = sseTokenService.generateToken(userId)

        val validated1 = sseTokenService.validateToken(token1)
        val validated2 = sseTokenService.validateToken(token2)

        assertEquals(userId, validated1, "Token 1 should still be valid")
        assertEquals(userId, validated2, "Token 2 should be valid")
    }

    @Test
    fun `should cleanup expired tokens on generation`() {
        val userId = 999L
        val baseTime = testTimeService.now()

        // Generate a token
        val token1 = sseTokenService.generateToken(userId)

        // Verify it's valid
        assertNotNull(sseTokenService.validateToken(token1), "Token should be valid initially")

        // Advance time to expire the first token
        testTimeService.setTime(baseTime.plusSeconds(31))

        // Generate a new token (this should trigger cleanup)
        val token2 = sseTokenService.generateToken(userId)

        // Verify the first token is now expired
        assertNull(sseTokenService.validateToken(token1), "First token should be expired")
        assertNotNull(sseTokenService.validateToken(token2), "Second token should be valid")
    }
}
