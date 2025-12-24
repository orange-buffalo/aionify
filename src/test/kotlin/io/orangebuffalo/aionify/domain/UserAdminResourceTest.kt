package io.orangebuffalo.aionify.domain

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestAuthSupport
import io.orangebuffalo.aionify.TestDatabaseSupport
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

/**
 * API endpoint security tests for user admin resource.
 * 
 * Per project guidelines, this test validates SECURITY ONLY, not business logic.
 * 
 * Tests verify:
 * 1. Admin role is required to access /api/admin/users endpoints
 * 2. Self-deletion is prevented at the API level
 */
@MicronautTest(transactional = false)
class UserAdminResourceTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    private val testPassword = "testPassword123"
    private lateinit var adminUser: User
    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()

        // Create admin user
        adminUser = testDatabaseSupport.insert(
            User.create(
                userName = "admin",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Admin User",
                isAdmin = true,
                locale = java.util.Locale.US
            )
        )
        
        // Create regular user
        regularUser = testDatabaseSupport.insert(
            User.create(
                userName = "user",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Regular User",
                isAdmin = false,
                locale = java.util.Locale.US
            )
        )
    }

    @Test
    fun `should require admin role to list users`() {
        // Given: A regular (non-admin) user token
        val regularUserToken = testAuthSupport.generateToken(regularUser)
        
        // When: Trying to access admin endpoint with non-admin token
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("/api/admin/users")
                    .bearerAuth(regularUserToken),
                String::class.java
            )
        }
        
        // Then: Access should be forbidden
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun `should allow admin role to list users`() {
        // Given: An admin user token
        val adminToken = testAuthSupport.generateToken(adminUser)
        
        // When: Accessing admin endpoint with admin token
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/api/admin/users")
                .bearerAuth(adminToken),
            UsersListResponse::class.java
        )
        
        // Then: Request should succeed
        assertEquals(HttpStatus.OK, response.status)
        assertNotNull(response.body())
    }

    @Test
    fun `should require authentication to list users`() {
        // When: Trying to access admin endpoint without authentication
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("/api/admin/users"),
                String::class.java
            )
        }
        
        // Then: Access should be unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should prevent self-deletion via API`() {
        // Given: An admin user token
        val adminToken = testAuthSupport.generateToken(adminUser)
        val adminId = requireNotNull(adminUser.id)
        
        // When: Admin tries to delete their own account via API
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.DELETE<Any>("/api/admin/users/$adminId")
                    .bearerAuth(adminToken),
                String::class.java
            )
        }
        
        // Then: Request should be rejected with bad request
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        // Check the response body contains the error message
        val responseBody = exception.response.getBody(String::class.java).orElse("")
        assertTrue(responseBody.contains("Cannot delete your own user account"), 
            "Expected error message in response body, got: $responseBody")
    }

    @Test
    fun `should allow admin to delete other users`() {
        // Given: An admin user token and another user to delete
        val adminToken = testAuthSupport.generateToken(adminUser)
        val userToDeleteId = requireNotNull(regularUser.id)
        
        // When: Admin deletes another user via API
        val response = client.toBlocking().exchange(
            HttpRequest.DELETE<Any>("/api/admin/users/$userToDeleteId")
                .bearerAuth(adminToken),
            String::class.java
        )
        
        // Then: Request should succeed
        assertEquals(HttpStatus.OK, response.status)
        
        // And: User should be deleted from database
        assertFalse(userRepository.existsById(userToDeleteId))
    }

    @Test
    fun `should require admin role to delete users`() {
        // Given: A regular (non-admin) user token
        val regularUserToken = testAuthSupport.generateToken(regularUser)
        val userToDeleteId = requireNotNull(adminUser.id)
        
        // When: Regular user tries to delete another user
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.DELETE<Any>("/api/admin/users/$userToDeleteId")
                    .bearerAuth(regularUserToken),
                String::class.java
            )
        }
        
        // Then: Access should be forbidden
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun `should require admin role to get user details`() {
        // Given: A regular (non-admin) user token
        val regularUserToken = testAuthSupport.generateToken(regularUser)
        val userId = requireNotNull(adminUser.id)
        
        // When: Regular user tries to get user details
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("/api/admin/users/$userId")
                    .bearerAuth(regularUserToken),
                String::class.java
            )
        }
        
        // Then: Access should be forbidden
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun `should allow admin to get user details`() {
        // Given: An admin user token
        val adminToken = testAuthSupport.generateToken(adminUser)
        val userId = requireNotNull(regularUser.id)
        
        // When: Admin gets user details
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/api/admin/users/$userId")
                .bearerAuth(adminToken),
            String::class.java
        )
        
        // Then: Request should succeed
        assertEquals(HttpStatus.OK, response.status)
    }

    @Test
    fun `should require admin role to update user`() {
        // Given: A regular (non-admin) user token
        val regularUserToken = testAuthSupport.generateToken(regularUser)
        val userId = requireNotNull(adminUser.id)
        
        // When: Regular user tries to update another user
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.PUT("/api/admin/users/$userId", mapOf("userName" to "newname"))
                    .bearerAuth(regularUserToken),
                String::class.java
            )
        }
        
        // Then: Access should be forbidden
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun `should allow admin to update user`() {
        // Given: An admin user token and a new username
        val adminToken = testAuthSupport.generateToken(adminUser)
        val userId = requireNotNull(regularUser.id)
        
        // When: Admin updates user
        val response = client.toBlocking().exchange(
            HttpRequest.PUT("/api/admin/users/$userId", mapOf("userName" to "updateduser"))
                .bearerAuth(adminToken),
            String::class.java
        )
        
        // Then: Request should succeed
        assertEquals(HttpStatus.OK, response.status)
        
        // And: Username should be updated
        val updatedUser = userRepository.findById(userId).get()
        assertEquals("updateduser", updatedUser.userName)
    }

    @Test
    fun `should require admin role to regenerate activation token`() {
        // Given: A regular (non-admin) user token
        val regularUserToken = testAuthSupport.generateToken(regularUser)
        val userId = requireNotNull(adminUser.id)
        
        // When: Regular user tries to regenerate activation token
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.POST("/api/admin/users/$userId/regenerate-activation-token", emptyMap<String, Any>())
                    .bearerAuth(regularUserToken),
                String::class.java
            )
        }
        
        // Then: Access should be forbidden
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun `should allow admin to regenerate activation token`() {
        // Given: An admin user token
        val adminToken = testAuthSupport.generateToken(adminUser)
        val userId = requireNotNull(regularUser.id)
        
        // When: Admin regenerates activation token
        val response = client.toBlocking().exchange(
            HttpRequest.POST("/api/admin/users/$userId/regenerate-activation-token", emptyMap<String, Any>())
                .bearerAuth(adminToken),
            String::class.java
        )
        
        // Then: Request should succeed
        assertEquals(HttpStatus.OK, response.status)
    }

    @Test
    fun `should require admin role to create users`() {
        // Given: A regular (non-admin) user token
        val regularUserToken = testAuthSupport.generateToken(regularUser)
        
        // When: Regular user tries to create a user
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.POST("/api/admin/users", mapOf(
                    "userName" to "newuser",
                    "greeting" to "New User",
                    "isAdmin" to false
                )).bearerAuth(regularUserToken),
                String::class.java
            )
        }
        
        // Then: Access should be forbidden
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun `should allow admin to create users`() {
        // Given: An admin user token
        val adminToken = testAuthSupport.generateToken(adminUser)
        
        // When: Admin creates a user
        val response = client.toBlocking().exchange(
            HttpRequest.POST("/api/admin/users", mapOf(
                "userName" to "newuser",
                "greeting" to "New User",
                "isAdmin" to false
            )).bearerAuth(adminToken),
            String::class.java
        )
        
        // Then: Request should succeed with CREATED status
        assertEquals(HttpStatus.CREATED, response.status)
        
        // And: User should exist in database
        val createdUser = userRepository.findByUserName("newuser").orElse(null)
        assertNotNull(createdUser)
        assertEquals("New User", createdUser.greeting)
        assertEquals(false, createdUser.isAdmin)
    }

    @Test
    fun `should require authentication to create users`() {
        // When: Trying to create user without authentication
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.POST("/api/admin/users", mapOf(
                    "userName" to "newuser",
                    "greeting" to "New User",
                    "isAdmin" to false
                )),
                String::class.java
            )
        }
        
        // Then: Access should be unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }
}
