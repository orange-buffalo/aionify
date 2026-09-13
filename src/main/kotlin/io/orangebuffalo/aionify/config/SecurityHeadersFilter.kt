package io.orangebuffalo.aionify.config

import io.micronaut.core.order.Ordered
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter

@ServerFilter(Filter.MATCH_ALL_PATTERN)
class SecurityHeadersFilter : Ordered {
    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    @ResponseFilter
    fun addSecurityHeaders(response: MutableHttpResponse<*>) {
        SECURITY_HEADERS.forEach { (name, value) ->
            if (!response.headers.contains(name)) {
                response.header(name, value)
            }
        }
    }

    companion object {
        private val SECURITY_HEADERS =
            mapOf(
                "Content-Security-Policy" to "frame-ancestors 'none'",
                "X-Frame-Options" to "DENY",
                "Cross-Origin-Opener-Policy" to "same-origin",
            )
    }
}
