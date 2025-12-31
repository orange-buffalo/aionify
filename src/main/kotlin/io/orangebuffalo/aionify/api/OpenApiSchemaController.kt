package io.orangebuffalo.aionify.api

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.models.OpenAPI
import org.yaml.snakeyaml.Yaml
import java.io.InputStreamReader

/**
 * Serves the OpenAPI schema for the public API.
 * The schema is generated at build time and merged with custom configuration at runtime.
 */
@Controller("/api")
@Secured(SecurityRule.IS_ANONYMOUS)
@Hidden
open class OpenApiSchemaController(
    private val openApiConfig: OpenAPI,
) {
    @Get("/schema", produces = [MediaType.APPLICATION_JSON])
    open fun getOpenApiSchema(): HttpResponse<String> {
        val inputStream =
            Thread
                .currentThread()
                .contextClassLoader
                .getResourceAsStream("META-INF/swagger/aionify-1.0.0.yml")
                ?: return HttpResponse.notFound()

        val yaml = Yaml()

        @Suppress("UNCHECKED_CAST")
        val schema = yaml.load<MutableMap<String, Any>>(InputStreamReader(inputStream))

        // Merge custom OpenAPI configuration
        if (openApiConfig.info != null) {
            @Suppress("UNCHECKED_CAST")
            val info = schema["info"] as MutableMap<String, Any>
            openApiConfig.info.title?.let { info["title"] = it }
            openApiConfig.info.version?.let { info["version"] = it }
            openApiConfig.info.description?.let { info["description"] = it }
        }

        if (openApiConfig.components?.securitySchemes != null) {
            @Suppress("UNCHECKED_CAST")
            val components = schema.getOrPut("components") { mutableMapOf<String, Any>() } as MutableMap<String, Any>

            @Suppress("UNCHECKED_CAST")
            val securitySchemes =
                components.getOrPut("securitySchemes") { mutableMapOf<String, Any>() } as MutableMap<String, Any>

            openApiConfig.components.securitySchemes.forEach { (name, scheme) ->
                securitySchemes[name] =
                    mapOf(
                        "type" to scheme.type.toString().lowercase(),
                        "scheme" to scheme.scheme,
                        "bearerFormat" to (scheme.bearerFormat ?: ""),
                        "description" to (scheme.description ?: ""),
                    )
            }
        }

        val content = yaml.dump(schema)
        return HttpResponse.ok(content).contentType(MediaType.TEXT_PLAIN_TYPE)
    }
}
