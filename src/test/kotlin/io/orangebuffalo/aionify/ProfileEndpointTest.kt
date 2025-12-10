package io.orangebuffalo.aionify

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.UserRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest
class ProfileEndpointTest {
    @Inject
    lateinit var userRepository: UserRepository
    
    @Inject
    lateinit var testUsers: TestUsers
    
    @Inject
    lateinit var testAuthSupport: TestAuthSupport
    
    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport
    
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient
    
    @BeforeEach
    fun setup() {
        testDatabaseSupport.truncateAllTables()
    }
    
    @Test
    fun `profile endpoint should return user data`() {
        // Create a test user
        val user = testUsers.createRegularUser(userRepository)
        println("Created user: ${user.userName} with ID: ${user.id}")
        
        // Generate token
        val token = testAuthSupport.generateToken(user)
        println("Generated token: ${token.take(50)}...")
        
        // Make request to profile endpoint
        val request = io.micronaut.http.HttpRequest.GET<Any>("/api/users/profile")
            .bearerAuth(token)
        
        println("Making request to: /api/users/profile")
        
        try {
            val response = client.toBlocking().exchange(request, String::class.java)
            
            println("Profile endpoint response status: ${response.status}")
            println("Profile endpoint response body: ${response.body()}")
            
            assert(response.status.code == 200) { "Expected 200 OK but got ${response.status}" }
        } catch (e: Exception) {
            println("ERROR: ${e.message}")
            println("Exception type: ${e.javaClass.name}")
            throw e
        }
    }
}
