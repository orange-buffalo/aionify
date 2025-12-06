package io.orangebuffalo.aionify.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger
import java.security.Principal

/**
 * Custom JWT authentication filter that validates JWT tokens using jjwt library.
 * Replaces SmallRye JWT for token validation to keep keys in-memory only.
 */
@Provider
@ApplicationScoped
class JwtAuthenticationFilter(
    private val jwtTokenService: JwtTokenService
) : ContainerRequestFilter {
    
    private val log = Logger.getLogger(JwtAuthenticationFilter::class.java)
    
    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
    
    override fun filter(requestContext: ContainerRequestContext) {
        val authHeader = requestContext.getHeaderString(AUTHORIZATION_HEADER)
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // No token, continue without authentication
            return
        }
        
        val token = authHeader.substring(BEARER_PREFIX.length)
        
        try {
            val claims = jwtTokenService.validateToken(token)
            val userName = claims.subject
            
            // Set security context with authenticated user
            requestContext.securityContext = object : SecurityContext {
                override fun getUserPrincipal(): Principal {
                    return Principal { userName }
                }
                
                override fun isUserInRole(role: String): Boolean {
                    // Check if user is admin based on token claims
                    val isAdmin = claims["isAdmin"] as? Boolean ?: false
                    return when (role) {
                        "admin" -> isAdmin
                        "user" -> true
                        else -> false
                    }
                }
                
                override fun isSecure(): Boolean {
                    return requestContext.uriInfo.requestUri.scheme == "https"
                }
                
                override fun getAuthenticationScheme(): String {
                    return "Bearer"
                }
            }
        } catch (e: Exception) {
            log.debug("JWT validation failed: ${e.message}")
            // Invalid token, but don't fail the request - let @Authenticated annotation handle it
        }
    }
}
