package io.orangebuffalo.aionify.config

import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter

@ServerFilter(Filter.MATCH_ALL_PATTERN)
class SecurityHeadersFilter : Ordered {
    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    @ResponseFilter
    fun addSecurityHeaders(
        request: HttpRequest<*>,
        response: MutableHttpResponse<*>,
    ) {
        SECURITY_HEADERS.forEach { (name, value) ->
            if (!response.headers.contains(name)) {
                response.header(name, value)
            }
        }

        when {
            response.contentType.orElse(null) == MediaType.TEXT_HTML_TYPE -> {
                response.header(HttpHeaders.CACHE_CONTROL, "no-cache")
            }

            HASHED_ASSET_PATH.matches(request.path) -> {
                response.header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            }
        }
    }

    companion object {
        private val HASHED_ASSET_PATH = Regex("^/[^/]+-[A-Za-z0-9_-]{8,}\\.(js|css)$")
        private val SECURITY_HEADERS =
            mapOf(
                "Content-Security-Policy" to "frame-ancestors 'none'",
                "X-Frame-Options" to "DENY",
                "Cross-Origin-Opener-Policy" to "same-origin",
            )
    }
}
