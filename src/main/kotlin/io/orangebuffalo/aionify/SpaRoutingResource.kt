package io.orangebuffalo.aionify

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.io.InputStream

/**
 * Handles SPA routing by serving index.html for all frontend routes.
 * This allows React Router to handle client-side routing.
 * Uses a negative condition to serve all paths except API endpoints.
 */
@Path("/")
class SpaRoutingResource {

    @GET
    @Path("{path:(?!api/).*}")
    @Produces(MediaType.TEXT_HTML)
    fun serveIndex(@PathParam("path") path: String): InputStream? {
        return Thread.currentThread().contextClassLoader
            .getResourceAsStream("META-INF/resources/index.html")
    }
}
