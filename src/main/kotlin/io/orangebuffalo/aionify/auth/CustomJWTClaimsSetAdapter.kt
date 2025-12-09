package io.orangebuffalo.aionify.auth

import com.nimbusds.jwt.JWTClaimsSet
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.jwt.validator.AuthenticationJWTClaimsSetAdapter
import jakarta.inject.Singleton

/**
 * Custom JWT claims adapter that extracts custom claims from JWT tokens
 * and makes them available in the security context.
 */
@Singleton
class CustomJWTClaimsSetAdapter : AuthenticationJWTClaimsSetAdapter {
    
    override fun getAuthentication(claimsSet: JWTClaimsSet): Authentication {
        val userName = claimsSet.subject
        val isAdmin = claimsSet.getClaim("isAdmin") as? Boolean ?: false
        
        val roles = if (isAdmin) listOf("admin", "user") else listOf("user")
        
        val attributes = mapOf(
            "userId" to claimsSet.getClaim("userId"),
            "greeting" to claimsSet.getClaim("greeting"),
            "isAdmin" to isAdmin
        )
        
        return Authentication.build(userName, roles, attributes)
    }
}
