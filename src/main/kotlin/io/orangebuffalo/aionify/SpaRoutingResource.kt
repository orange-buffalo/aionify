package io.orangebuffalo.aionify

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.io.InputStream

/**
 * Handles SPA routing by serving index.html for all frontend routes.
 * This allows React Router to handle client-side routing.
 */
@Path("/")
class SpaRoutingResource {

    @GET
    @Path("{path:login|admin|admin/settings|portal|portal/settings}")
    @Produces(MediaType.TEXT_HTML)
    fun serveIndex(): InputStream? {
        return Thread.currentThread().contextClassLoader
            .getResourceAsStream("META-INF/resources/index.html")
    }
}
