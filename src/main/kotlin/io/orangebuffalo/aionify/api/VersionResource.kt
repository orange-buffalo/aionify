package io.orangebuffalo.aionify.api

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Controller("/api")
@Tag(name = "Public API", description = "Public API endpoints")
open class VersionResource {
    @Get("/version")
    @Operation(
        summary = "Get API version",
        description = "Returns the current version of the API",
        security = [SecurityRequirement(name = "BearerAuth")],
    )
    @ApiResponse(
        responseCode = "200",
        description = "API version",
        content = [Content(schema = Schema(implementation = VersionResponse::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized - Invalid or missing API token",
    )
    @ApiResponse(
        responseCode = "429",
        description = "Too Many Requests - IP blocked due to too many failed auth attempts",
    )
    open fun getVersion(): VersionResponse = VersionResponse(version = "1.0")
}

@Serdeable
@Introspected
@Schema(description = "API version response")
data class VersionResponse(
    @field:Schema(description = "API version number", example = "1.0")
    val version: String,
)
