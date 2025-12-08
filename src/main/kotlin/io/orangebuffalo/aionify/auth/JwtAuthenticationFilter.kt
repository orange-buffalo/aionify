package io.orangebuffalo.aionify.auth

import com.auth0.jwt.interfaces.DecodedJWT
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.SecurityContext
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger
import java.security.Principal

/**
 * Custom JWT authentication filter that validates JWT tokens using Auth0 java-jwt library.
 * Validates tokens and sets security context for authenticated requests.
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
            val jwt = jwtTokenService.validateToken(token)
            val userName = jwt.subject
            
            if (userName.isNullOrBlank()) {
                log.debug("JWT token has no subject claim")
                return
            }
            
            // Set security context with authenticated user
            requestContext.securityContext = object : SecurityContext {
                override fun getUserPrincipal(): Principal {
                    return Principal { userName }
                }
                
                override fun isUserInRole(role: String): Boolean {
                    // Check if user is admin based on token claims
                    val isAdmin = jwt.getClaim("isAdmin").asBoolean() ?: false
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
