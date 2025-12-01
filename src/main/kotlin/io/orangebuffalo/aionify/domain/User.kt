package io.orangebuffalo.aionify.domain

import java.util.Locale

data class User(
    val id: Long? = null,
    val userName: String,
    val passwordHash: String,
    val greeting: String,
    val isAdmin: Boolean,
    val locale: Locale,
    val languageCode: String
)
