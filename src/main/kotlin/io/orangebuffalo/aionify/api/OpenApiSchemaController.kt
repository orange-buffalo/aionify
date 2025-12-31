package io.orangebuffalo.aionify.api

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import java.io.InputStreamReader

/**
 * Serves the OpenAPI schema for the public API.
 * The schema is generated at build time and served from META-INF/swagger.
 */
@Controller("/api")
@Secured(SecurityRule.IS_ANONYMOUS)
open class OpenApiSchemaController {
    @Get("/schema", produces = [MediaType.APPLICATION_JSON])
    open fun getOpenApiSchema(): HttpResponse<String> {
        val inputStream =
            Thread
                .currentThread()
                .contextClassLoader
                .getResourceAsStream("META-INF/swagger/aionify-1.0.0.yml")
                ?: return HttpResponse.notFound()

        val content = InputStreamReader(inputStream).readText()
        return HttpResponse.ok(content).contentType(MediaType.TEXT_PLAIN_TYPE)
    }
}
