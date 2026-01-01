package io.orangebuffalo.aionify.domain

import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.utils.SecurityService
import jakarta.inject.Singleton
import java.util.Optional

/**
 * Binds the current authenticated user to method parameters of type [UserWithId].
 * This binder extracts the principal from the security context and resolves the
 * corresponding user from the database.
 *
 * For API requests (/api/), the authentication is retrieved from request attributes
 * (set by ApiAuthenticationFilter). For UI requests, it's retrieved from SecurityService.
 */
@Singleton
class CurrentUserArgumentBinder(
    private val currentUserService: CurrentUserService,
    private val securityService: SecurityService,
) : TypedRequestArgumentBinder<UserWithId> {
    override fun argumentType(): Argument<UserWithId> = Argument.of(UserWithId::class.java)

    override fun bind(
        context: ArgumentConversionContext<UserWithId>,
        source: HttpRequest<*>,
    ): ArgumentBinder.BindingResult<UserWithId> {
        // For API requests, get authentication from request attributes
        val authentication =
            if (source.path.startsWith("/api/")) {
                source.getAttribute("micronaut.security.AUTHENTICATION", Authentication::class.java).orElse(null)
            } else {
                securityService.authentication.orElse(null)
            }

        val principal = authentication?.let { java.security.Principal { it.name } }
        val userWithId = currentUserService.resolveUser(principal)
        return ArgumentBinder.BindingResult { Optional.of(userWithId) }
    }
}
