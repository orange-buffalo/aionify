package io.orangebuffalo.aionify.api

import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import io.micronaut.security.authentication.Authentication
import io.orangebuffalo.aionify.domain.CurrentUserService
import io.orangebuffalo.aionify.domain.UserWithId
import jakarta.inject.Singleton
import java.util.Optional

/**
 * Binds the current authenticated user to method parameters of type [UserWithId] for API endpoints.
 * This binder extracts the authentication from request attributes (set by ApiAuthenticationFilter)
 * and resolves the corresponding user from the database.
 */
@Singleton
class ApiUserWithIdArgumentBinder(
    private val currentUserService: CurrentUserService,
) : TypedRequestArgumentBinder<UserWithId> {
    override fun argumentType(): Argument<UserWithId> = Argument.of(UserWithId::class.java)

    override fun bind(
        context: ArgumentConversionContext<UserWithId>,
        source: HttpRequest<*>,
    ): ArgumentBinder.BindingResult<UserWithId> {
        // Check if this is an API request
        if (!source.path.startsWith("/api/")) {
            // Not an API request, return empty to let the default binder handle it
            return ArgumentBinder.BindingResult { Optional.empty() }
        }

        // Get authentication from request attributes (set by ApiAuthenticationFilter)
        val authentication =
            source.getAttribute("micronaut.security.AUTHENTICATION", Authentication::class.java).orElse(null)

        val principal = authentication?.let { java.security.Principal { it.name } }
        val userWithId = currentUserService.resolveUser(principal)
        return ArgumentBinder.BindingResult { Optional.of(userWithId) }
    }
}
