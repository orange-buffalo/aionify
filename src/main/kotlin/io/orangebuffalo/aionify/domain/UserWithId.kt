package io.orangebuffalo.aionify.domain

/**
 * Represents a user with a guaranteed non-null ID.
 * Used for authenticated endpoints where we know the user exists in the database.
 */
data class UserWithId(
    val user: User,
    val id: Long,
)
