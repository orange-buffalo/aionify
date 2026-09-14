package io.orangebuffalo.aionify

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.api.ApiRateLimitingService
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class SecurityHeadersFilterTest {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var apiRateLimitingService: ApiRateLimitingService

    @BeforeEach
    fun clearRateLimits() {
        apiRateLimitingService.clearAllAttempts()
    }

    @Test
    fun `should add security headers to application responses`() {
        assertResponse("/", HttpStatus.OK)
        assertResponse("/dashboard", HttpStatus.OK)
        assertResponse("/favicon.svg", HttpStatus.OK)
        assertResponse("/favicon.ico", HttpStatus.NOT_FOUND)
        assertResponse("/api/schema", HttpStatus.OK)
        assertResponse("/api/time-log-entries/active", HttpStatus.UNAUTHORIZED)
        assertResponse("/api-ui/time-log-entries/active", HttpStatus.UNAUTHORIZED)
        assertResponse("/api-ui/time-log-entries/events", HttpStatus.UNAUTHORIZED)
        assertResponse("/security-headers-test/error", HttpStatus.INTERNAL_SERVER_ERROR)

        apiRateLimitingService.clearAllAttempts()
        repeat(10) {
            exchange("/api/time-log-entries/active")
        }
        assertResponse("/api/time-log-entries/active", HttpStatus.TOO_MANY_REQUESTS)
    }

    @Test
    fun `should prevent stale frontend bundles after deployments`() {
        val htmlResponse = exchange("/")
        assertEquals("no-cache", htmlResponse.headers["Cache-Control"])

        val html = htmlResponse.body() as String
        val scriptPath = requireNotNull(Regex("""src="(/main-[^"]+\.js)"""").find(html)?.groupValues?.get(1))
        val stylesPath = requireNotNull(Regex("""href="(/styles-[^"]+\.css)"""").find(html)?.groupValues?.get(1))

        assertImmutableAsset(scriptPath)
        assertImmutableAsset(stylesPath)
    }

    private fun assertResponse(
        path: String,
        expectedStatus: HttpStatus,
    ) {
        val response = exchange(path)

        assertEquals(expectedStatus, response.status, path)
        assertEquals("frame-ancestors 'none'", response.headers["Content-Security-Policy"], path)
        assertEquals("DENY", response.headers["X-Frame-Options"], path)
        assertEquals("same-origin", response.headers["Cross-Origin-Opener-Policy"], path)
    }

    private fun assertImmutableAsset(path: String) {
        val response = exchange(path)

        assertEquals(HttpStatus.OK, response.status, path)
        assertEquals("public, max-age=31536000, immutable", response.headers["Cache-Control"], path)
    }

    private fun exchange(path: String): HttpResponse<*> =
        try {
            client.toBlocking().exchange(HttpRequest.GET<Any>(path), String::class.java)
        } catch (exception: HttpClientResponseException) {
            exception.response
        }
}

@Controller("/security-headers-test")
@Secured(SecurityRule.IS_ANONYMOUS)
class SecurityHeadersTestController {
    @Get("/error")
    fun error(): String = throw IllegalStateException("Intentional test error")
}
