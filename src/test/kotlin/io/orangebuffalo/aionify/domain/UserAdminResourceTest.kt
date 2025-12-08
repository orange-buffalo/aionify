package io.orangebuffalo.aionify.domain

import io.orangebuffalo.aionify.TestAuthSupport
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.quarkus.elytron.security.common.BcryptUtil
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * API endpoint tests for user admin resource.
 * Tests business logic including self-deletion prevention and pagination.
 * 
 * Note: @QuarkusTest mode does not enforce security annotations by default.
 * Authentication and authorization security is comprehensively tested via Playwright tests
 * which run against the production Docker image.
 */
@QuarkusTest
class UserAdminResourceTest {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    private val testPassword = "testPassword123"
    private lateinit var adminUser: User
    private lateinit var regularUser: User
    private lateinit var adminToken: String

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()

        // Create admin user
        adminUser = userRepository.insert(
            User(
                userName = "admin",
                passwordHash = BcryptUtil.bcryptHash(testPassword),
                greeting = "Admin User",
                isAdmin = true,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )
        adminToken = testAuthSupport.generateToken(adminUser)

        // Create regular user
        regularUser = userRepository.insert(
            User(
                userName = "regularuser",
                passwordHash = BcryptUtil.bcryptHash(testPassword),
                greeting = "Regular User",
                isAdmin = false,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )
    }

    @Test
    fun `should list users when authenticated as admin`() {
        given()
            .header("Authorization", "Bearer $adminToken")
            .`when`()
            .get("/api/admin/users")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("users.size()", equalTo(2))
            .body("total", equalTo(2))
            .body("page", equalTo(0))
            .body("size", equalTo(20))
            .body("users[0].userName", equalTo("admin"))
            .body("users[0].greeting", equalTo("Admin User"))
            .body("users[0].isAdmin", equalTo(true))
            .body("users[1].userName", equalTo("regularuser"))
            .body("users[1].greeting", equalTo("Regular User"))
            .body("users[1].isAdmin", equalTo(false))
    }

    @Test
    fun `should return users sorted by username`() {
        // Create users with names that would be out of order if not sorted
        userRepository.insert(
            User(
                userName = "zebra",
                passwordHash = BcryptUtil.bcryptHash(testPassword),
                greeting = "Zebra User",
                isAdmin = false,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )
        userRepository.insert(
            User(
                userName = "apple",
                passwordHash = BcryptUtil.bcryptHash(testPassword),
                greeting = "Apple User",
                isAdmin = false,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )

        given()
            .header("Authorization", "Bearer $adminToken")
            .`when`()
            .get("/api/admin/users")
            .then()
            .statusCode(200)
            .body("users.size()", equalTo(4))
            .body("users[0].userName", equalTo("admin"))
            .body("users[1].userName", equalTo("apple"))
            .body("users[2].userName", equalTo("regularuser"))
            .body("users[3].userName", equalTo("zebra"))
    }

    @Test
    fun `should support pagination with custom page and size`() {
        // Create 25 additional users
        for (i in 1..25) {
            userRepository.insert(
                User(
                    userName = "user$i",
                    passwordHash = BcryptUtil.bcryptHash(testPassword),
                    greeting = "User $i",
                    isAdmin = false,
                    locale = Locale.ENGLISH,
                    languageCode = "en"
                )
            )
        }

        // Get first page with size 10
        given()
            .header("Authorization", "Bearer $adminToken")
            .queryParam("page", 0)
            .queryParam("size", 10)
            .`when`()
            .get("/api/admin/users")
            .then()
            .statusCode(200)
            .body("users.size()", equalTo(10))
            .body("total", equalTo(27))
            .body("page", equalTo(0))
            .body("size", equalTo(10))

        // Get second page
        given()
            .header("Authorization", "Bearer $adminToken")
            .queryParam("page", 1)
            .queryParam("size", 10)
            .`when`()
            .get("/api/admin/users")
            .then()
            .statusCode(200)
            .body("users.size()", equalTo(10))
            .body("page", equalTo(1))

        // Get third page (should have 7 users)
        given()
            .header("Authorization", "Bearer $adminToken")
            .queryParam("page", 2)
            .queryParam("size", 10)
            .`when`()
            .get("/api/admin/users")
            .then()
            .statusCode(200)
            .body("users.size()", equalTo(7))
            .body("page", equalTo(2))
    }

    @Test
    fun `should return 400 when page is negative`() {
        given()
            .header("Authorization", "Bearer $adminToken")
            .queryParam("page", -1)
            .`when`()
            .get("/api/admin/users")
            .then()
            .statusCode(400)
            .body("error", equalTo("Page must be non-negative"))
    }

    @Test
    fun `should return 400 when size is zero`() {
        given()
            .header("Authorization", "Bearer $adminToken")
            .queryParam("size", 0)
            .`when`()
            .get("/api/admin/users")
            .then()
            .statusCode(400)
            .body("error", equalTo("Size must be between 1 and 100"))
    }

    @Test
    fun `should return 400 when size exceeds maximum`() {
        given()
            .header("Authorization", "Bearer $adminToken")
            .queryParam("size", 101)
            .`when`()
            .get("/api/admin/users")
            .then()
            .statusCode(400)
            .body("error", equalTo("Size must be between 1 and 100"))
    }

    @Test
    fun `should delete user when authenticated as admin`() {
        given()
            .header("Authorization", "Bearer $adminToken")
            .`when`()
            .delete("/api/admin/users/${regularUser.id}")
            .then()
            .statusCode(200)
            .body("message", equalTo("User deleted successfully"))

        // Verify user is deleted
        val deletedUser = userRepository.findById(regularUser.id!!)
        assert(deletedUser == null) { "User should be deleted from database" }
    }

    @Test
    fun `should return 400 when trying to delete own user account`() {
        given()
            .header("Authorization", "Bearer $adminToken")
            .`when`()
            .delete("/api/admin/users/${adminUser.id}")
            .then()
            .statusCode(400)
            .body("error", equalTo("Cannot delete your own user account"))

        // Verify user is not deleted
        val user = userRepository.findById(adminUser.id!!)
        assert(user != null) { "User should not be deleted" }
    }

    @Test
    fun `should return 404 when deleting non-existent user`() {
        val nonExistentId = 99999L

        given()
            .header("Authorization", "Bearer $adminToken")
            .`when`()
            .delete("/api/admin/users/$nonExistentId")
            .then()
            .statusCode(404)
            .body("error", equalTo("User not found"))
    }

    @Test
    fun `should return correct user information including ID`() {
        given()
            .header("Authorization", "Bearer $adminToken")
            .`when`()
            .get("/api/admin/users")
            .then()
            .statusCode(200)
            .body("users[0].id", notNullValue())
            .body("users[0].id", equalTo(adminUser.id!!.toInt()))
            .body("users[1].id", notNullValue())
            .body("users[1].id", equalTo(regularUser.id!!.toInt()))
    }
}
