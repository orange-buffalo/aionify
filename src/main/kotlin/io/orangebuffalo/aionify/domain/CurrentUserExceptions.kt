package io.orangebuffalo.aionify.domain

/**
 * Thrown when a request is made without authentication.
 */
class UserNotAuthenticatedException(
    message: String = "User not authenticated",
) : RuntimeException(message)

/**
 * Thrown when an authenticated user is not found in the database.
 */
class UserNotFoundException(
    val userName: String,
    message: String = "User not found: $userName",
) : RuntimeException(message)
