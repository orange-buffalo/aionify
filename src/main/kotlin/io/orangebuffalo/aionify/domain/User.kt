package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Transient
import java.util.Locale

@MappedEntity("app_user")
data class User(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long? = null,
    
    @field:MappedProperty("user_name")
    val userName: String,
    
    @field:MappedProperty("password_hash")
    val passwordHash: String,
    
    val greeting: String,
    
    @field:MappedProperty("is_admin")
    val isAdmin: Boolean,
    
    @field:MappedProperty("locale")
    val localeTag: String
) {
    companion object {
        private val SUPPORTED_LANGUAGES = setOf("en", "uk")
        
        fun create(
            id: Long? = null,
            userName: String,
            passwordHash: String,
            greeting: String,
            isAdmin: Boolean,
            locale: Locale
        ) = User(
            id = id,
            userName = userName,
            passwordHash = passwordHash,
            greeting = greeting,
            isAdmin = isAdmin,
            localeTag = locale.toLanguageTag()
        )
    }
    
    /**
     * Derives the language code from the locale tag.
     * Returns the language part of the locale (e.g., "en" from "en-US").
     * Falls back to "en" if the language is not in the supported languages list.
     */
    @get:Transient
    val languageCode: String
        get() {
            val language = Locale.forLanguageTag(localeTag).language
            return if (language in SUPPORTED_LANGUAGES) language else "en"
        }
}
