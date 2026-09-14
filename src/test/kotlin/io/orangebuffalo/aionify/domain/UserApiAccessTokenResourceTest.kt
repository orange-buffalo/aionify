package io.orangebuffalo.aionify.domain

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestAuthSupport
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.TestUsers
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Security tests for API access token management: users must not be able to access tokens of other users,
 * and token management rules must not be bypassable via the API.
 */
@MicronautTest(transactional = false)
class UserApiAccessTokenResourceTest {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    @Inject
    lateinit var testUsers: TestUsers

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var userApiAccessTokenRepository: UserApiAccessTokenRepository

    @Inject
    lateinit var timeService: TimeService

    private lateinit var owner: User
    private lateinit var otherUser: User
    private lateinit var ownerToken: UserApiAccessToken

    companion object {
        private const val BASE_URL = "/api-ui/users/api-tokens"
        private const val OWNER_TOKEN_VALUE = "ownerTokenValue1234567890"
    }

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()
        owner = testUsers.createRegularUser("tokenOwner", "Token Owner")
        otherUser = testUsers.createRegularUser("otherTokenUser", "Other Token User")
        ownerToken =
            testDatabaseSupport.insert(
                UserApiAccessToken(
                    userId = requireNotNull(owner.id),
                    token = OWNER_TOKEN_VALUE,
                    name = "Owner Integration",
                    createdAt = timeService.now(),
                ),
            )
    }

    @Test
    fun `should require authentication to list tokens`() {
        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(HttpRequest.GET<Any>(BASE_URL), String::class.java)
            }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should list only own tokens`() {
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = requireNotNull(otherUser.id),
                token = "otherTokenValue1234567890",
                name = "Other Integration",
                createdAt = timeService.now(),
            ),
        )

        val response =
            client.toBlocking().exchange(
                HttpRequest.GET<Any>(BASE_URL).bearerAuth(jwt(otherUser)),
                ApiAccessTokensResponse::class.java,
            )

        assertEquals(listOf("Other Integration"), response.body()!!.tokens.map { it.name })
    }

    @Test
    fun `should not reveal token of another user`() {
        assertErrorResponse(HttpStatus.NOT_FOUND, "API_TOKEN_NOT_FOUND") {
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("$BASE_URL/${ownerToken.id}/value").bearerAuth(jwt(otherUser)),
                ApiAccessTokenValueResponse::class.java,
            )
        }
    }

    @Test
    fun `should not regenerate token of another user`() {
        assertErrorResponse(HttpStatus.NOT_FOUND, "API_TOKEN_NOT_FOUND") {
            client.toBlocking().exchange(
                HttpRequest.PUT("$BASE_URL/${ownerToken.id}", emptyMap<String, Any>()).bearerAuth(jwt(otherUser)),
                ApiAccessTokenSummary::class.java,
            )
        }

        testDatabaseSupport.inTransaction {
            val token = userApiAccessTokenRepository.findById(requireNotNull(ownerToken.id))
            assertTrue(token.isPresent, "Token should still exist")
            assertEquals(OWNER_TOKEN_VALUE, token.get().token, "Token value should not change")
        }
    }

    @Test
    fun `should not delete token of another user`() {
        assertErrorResponse(HttpStatus.NOT_FOUND, "API_TOKEN_NOT_FOUND") {
            client.toBlocking().exchange(
                HttpRequest.DELETE<Any>("$BASE_URL/${ownerToken.id}").bearerAuth(jwt(otherUser)),
                ApiAccessTokenSuccessResponse::class.java,
            )
        }

        testDatabaseSupport.inTransaction {
            assertTrue(userApiAccessTokenRepository.findById(requireNotNull(ownerToken.id)).isPresent, "Token should still exist")
        }
    }

    @Test
    fun `should reject token with a name already used by the same user`() {
        assertErrorResponse(HttpStatus.BAD_REQUEST, "API_TOKEN_NAME_ALREADY_EXISTS") {
            createToken(owner, " Owner Integration ")
        }
    }

    @Test
    fun `should allow token with a name used by another user`() {
        val response = createToken(otherUser, "Owner Integration")

        assertEquals("Owner Integration", response.name)
        assertEquals(50, response.token.length)
    }

    @Test
    fun `should reject token with blank name`() {
        assertErrorResponse(HttpStatus.BAD_REQUEST, "API_TOKEN_NAME_REQUIRED") {
            createToken(owner, "   ")
        }
    }

    @Test
    fun `should reject token with too long name`() {
        assertErrorResponse(HttpStatus.BAD_REQUEST, "API_TOKEN_NAME_TOO_LONG") {
            createToken(owner, "a".repeat(UserApiAccessTokenResource.MAX_NAME_LENGTH + 1))
        }
    }

    @Test
    fun `should reject new token when the limit is reached`() {
        val ownerId = requireNotNull(owner.id)
        repeat(UserApiAccessTokenResource.MAX_TOKENS_PER_USER - 1) { index ->
            testDatabaseSupport.insert(
                UserApiAccessToken(
                    userId = ownerId,
                    token = "limitTokenValue$index",
                    name = "Integration $index",
                    createdAt = timeService.now(),
                ),
            )
        }

        assertErrorResponse(HttpStatus.BAD_REQUEST, "API_TOKEN_LIMIT_REACHED") {
            createToken(owner, "One Too Many")
        }
    }

    private fun jwt(user: User) = testAuthSupport.generateToken(user)

    private fun createToken(
        user: User,
        name: String,
    ): CreatedApiAccessTokenResponse =
        client
            .toBlocking()
            .exchange(
                HttpRequest.POST(BASE_URL, CreateApiAccessTokenRequest(name = name)).bearerAuth(jwt(user)),
                CreatedApiAccessTokenResponse::class.java,
            ).body()!!

    private fun assertErrorResponse(
        expectedStatus: HttpStatus,
        expectedErrorCode: String,
        request: () -> Unit,
    ) {
        val exception = assertThrows(HttpClientResponseException::class.java) { request() }
        assertEquals(expectedStatus, exception.status)
        assertEquals(
            expectedErrorCode,
            exception.response
                .getBody(ApiAccessTokenErrorResponse::class.java)
                .get()
                .errorCode,
        )
    }
}
