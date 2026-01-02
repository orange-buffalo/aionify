package io.orangebuffalo.aionify.domain

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep

@MicronautTest(transactional = false)
class SseTokenServiceTest {
    @Inject
    lateinit var sseTokenService: SseTokenService

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
        val token = sseTokenService.generateToken(userId)

        // Wait for token to expire (30 seconds + buffer)
        // Note: This test takes 31 seconds to run
        sleep(31000)

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
}
