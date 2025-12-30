package io.orangebuffalo.aionify.domain

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton

/**
 * Handles [UserNotAuthenticatedException] by returning an UNAUTHORIZED response.
 */
@Produces
@Singleton
@Requires(classes = [UserNotAuthenticatedException::class, ExceptionHandler::class])
class UserNotAuthenticatedExceptionHandler : ExceptionHandler<UserNotAuthenticatedException, HttpResponse<GenericErrorResponse>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: UserNotAuthenticatedException,
    ): HttpResponse<GenericErrorResponse> =
        HttpResponse
            .status<GenericErrorResponse>(HttpStatus.UNAUTHORIZED)
            .body(GenericErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
}

/**
 * Handles [UserNotFoundException] by returning a NOT_FOUND response.
 */
@Produces
@Singleton
@Requires(classes = [UserNotFoundException::class, ExceptionHandler::class])
class UserNotFoundExceptionHandler : ExceptionHandler<UserNotFoundException, HttpResponse<GenericErrorResponse>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: UserNotFoundException,
    ): HttpResponse<GenericErrorResponse> =
        HttpResponse
            .status<GenericErrorResponse>(HttpStatus.NOT_FOUND)
            .body(GenericErrorResponse("User not found", "USER_NOT_FOUND"))
}

/**
 * Generic error response used by exception handlers.
 */
@Serdeable
@Introspected
data class GenericErrorResponse(
    val error: String,
    val errorCode: String,
)
