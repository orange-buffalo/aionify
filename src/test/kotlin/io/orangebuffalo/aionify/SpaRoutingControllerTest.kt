package io.orangebuffalo.aionify

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for SpaRoutingController to ensure static assets are not caught by the SPA routing.
 */
@MicronautTest
class SpaRoutingControllerTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun `should not return HTML for favicon requests`() {
        // When: Browser requests favicon.ico
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("/favicon.ico"),
                String::class.java
            )
        }
        
        // Then: Should return 404 (not found) instead of returning HTML
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `should not return HTML for SVG icon requests`() {
        // When: Browser requests an SVG icon
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("/logo.svg"),
                String::class.java
            )
        }
        
        // Then: Should return 404 (not found) instead of returning HTML
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `should not return HTML for PNG image requests`() {
        // When: Browser requests a PNG image
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("/image.png"),
                String::class.java
            )
        }
        
        // Then: Should return 404 (not found) instead of returning HTML
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `should not return HTML for font file requests`() {
        // When: Browser requests a font file
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("/fonts/myfont.woff2"),
                String::class.java
            )
        }
        
        // Then: Should return 404 (not found) instead of returning HTML
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `should return HTML for SPA routes`() {
        // When: Browser requests a frontend route
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/dashboard"),
            String::class.java
        )
        
        // Then: Should return HTML with 200 OK
        assertEquals(HttpStatus.OK, response.status)
        assertEquals(MediaType.TEXT_HTML_TYPE, response.contentType.orElse(null))
        assertNotNull(response.body())
        assertTrue(response.body()!!.contains("<!DOCTYPE html>"))
    }

    @Test
    fun `should return HTML for root path`() {
        // When: Browser requests the root path
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/"),
            String::class.java
        )
        
        // Then: Should return HTML with 200 OK
        assertEquals(HttpStatus.OK, response.status)
        assertEquals(MediaType.TEXT_HTML_TYPE, response.contentType.orElse(null))
        assertNotNull(response.body())
        assertTrue(response.body()!!.contains("<!DOCTYPE html>"))
    }
}
