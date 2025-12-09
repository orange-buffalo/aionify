package io.orangebuffalo.aionify

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import java.io.InputStream

/**
 * Handles SPA routing by serving index.html for all frontend routes.
 * This allows React Router to handle client-side routing.
 */
@Controller
@Secured(SecurityRule.IS_ANONYMOUS)
open class SpaRoutingController {

    @Get(uri = "/{path:(?!api/).*}", produces = [MediaType.TEXT_HTML])
    open fun serveIndex(request: HttpRequest<*>): HttpResponse<InputStream> {
        val inputStream = Thread.currentThread().contextClassLoader
            .getResourceAsStream("META-INF/resources/index.html")
            ?: return HttpResponse.notFound()
        
        return HttpResponse.ok(inputStream).contentType(MediaType.TEXT_HTML_TYPE)
    }
}
