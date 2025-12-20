package io.orangebuffalo.aionify.domain

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestTransactionHelper
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID

@MicronautTest(transactional = false)
class ActivationTokenServiceTest {

    @Inject
    lateinit var activationTokenService: ActivationTokenService
    
    @Inject
    lateinit var userRepository: UserRepository
    
    @Inject
    lateinit var activationTokenRepository: ActivationTokenRepository
    
    @Inject
    lateinit var transactionHelper: TestTransactionHelper
    
    private fun createTestUser(): User {
        return transactionHelper.inTransaction {
            userRepository.save(
                User.create(
                    userName = "tokentest-${UUID.randomUUID()}",
                    passwordHash = BCrypt.hashpw("password", BCrypt.gensalt()),
                    greeting = "Token Test",
                    isAdmin = false,
                    locale = Locale.ENGLISH,
                    languageCode = "en"
                )
            )
        }
    }

    @Test
    fun `should create activation token with 10 days expiration by default`() {
        val testUser = createTestUser()
        
        // Create token with default expiration
        val token = transactionHelper.inTransaction {
            activationTokenService.createToken(requireNotNull(testUser.id))
        }
        
        assertNotNull(token.token)
        assertNotNull(token.expiresAt)
        
        // Calculate the duration between now and expiration
        val now = Instant.now()
        val duration = Duration.between(now, token.expiresAt)
        
        // Should be approximately 10 days (240 hours)
        // Allow for a small tolerance due to execution time
        val tenDaysInSeconds = 10L * 24 * 60 * 60
        val actualSeconds = duration.seconds
        
        // Assert it's within a reasonable range (9.9 to 10 days)
        assertTrue(actualSeconds >= tenDaysInSeconds - 3600, 
            "Token expiration should be at least 9.9 days, but was ${actualSeconds}s")
        assertTrue(actualSeconds <= tenDaysInSeconds, 
            "Token expiration should be at most 10 days, but was ${actualSeconds}s")
    }
    
    @Test
    fun `should allow custom expiration time`() {
        val testUser = createTestUser()
        
        // Create token with custom 48 hour expiration
        val token = transactionHelper.inTransaction {
            activationTokenService.createToken(requireNotNull(testUser.id), expirationHours = 48)
        }
        
        assertNotNull(token.token)
        assertNotNull(token.expiresAt)
        
        // Calculate the duration
        val now = Instant.now()
        val duration = Duration.between(now, token.expiresAt)
        
        // Should be approximately 48 hours
        val twoDaysInSeconds = 48L * 60 * 60
        val actualSeconds = duration.seconds
        
        // Assert it's within a reasonable range
        assertTrue(actualSeconds >= twoDaysInSeconds - 3600, 
            "Token expiration should be at least 47 hours, but was ${actualSeconds}s")
        assertTrue(actualSeconds <= twoDaysInSeconds, 
            "Token expiration should be at most 48 hours, but was ${actualSeconds}s")
    }
    
    @Test
    fun `should delete any existing tokens when creating new one`() {
        val testUser = createTestUser()
        
        // Create first token
        val firstToken = transactionHelper.inTransaction {
            activationTokenService.createToken(requireNotNull(testUser.id))
        }
        
        // Verify it exists
        val foundFirst = transactionHelper.inTransaction {
            activationTokenRepository.findByToken(firstToken.token)
        }
        assertTrue(foundFirst.isPresent)
        
        // Create second token - should delete the first one
        val secondToken = transactionHelper.inTransaction {
            activationTokenService.createToken(requireNotNull(testUser.id))
        }
        
        // First token should no longer exist
        val foundFirstAfter = transactionHelper.inTransaction {
            activationTokenRepository.findByToken(firstToken.token)
        }
        assertTrue(foundFirstAfter.isEmpty)
        
        // Second token should exist
        val foundSecond = transactionHelper.inTransaction {
            activationTokenRepository.findByToken(secondToken.token)
        }
        assertTrue(foundSecond.isPresent)
    }
}
