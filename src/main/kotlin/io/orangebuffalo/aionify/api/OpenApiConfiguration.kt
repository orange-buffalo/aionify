package io.orangebuffalo.aionify.api

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme

/**
 * OpenAPI configuration for the public API.
 * Configures the API documentation with bearer token authentication.
 */
@Factory
class OpenApiConfiguration {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Aionify Public API")
                    .version("1.0")
                    .description(
                        """
                        Public API for Aionify application.
                        
                        ## Authentication
                        
                        All API endpoints (except /api/schema) require authentication using a Bearer token.
                        
                        To authenticate:
                        1. Generate an API token in the Aionify web application (User Settings page)
                        2. Include the token in the Authorization header of your requests:
                           ```
                           Authorization: Bearer YOUR_API_TOKEN
                           ```
                        
                        ## Rate Limiting
                        
                        To protect against brute force attacks, the API implements rate limiting:
                        - After 10 consecutive failed authentication attempts from the same IP address, 
                          that IP will be blocked for 10 minutes
                        - Successful authentication clears the failed attempt counter
                        - HTTP 429 (Too Many Requests) is returned when an IP is blocked
                        """.trimIndent(),
                    ),
            ).components(
                Components()
                    .addSecuritySchemes(
                        "BearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("opaque")
                            .description("API token generated in the Aionify web application"),
                    ),
            )
}
