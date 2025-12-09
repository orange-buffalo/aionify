package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
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
    val localeTag: String,
    
    @field:MappedProperty("language_code")
    val languageCode: String
) {
    val locale: Locale
        get() = Locale.forLanguageTag(localeTag)
    
    companion object {
        fun create(
            id: Long? = null,
            userName: String,
            passwordHash: String,
            greeting: String,
            isAdmin: Boolean,
            locale: Locale,
            languageCode: String
        ) = User(
            id = id,
            userName = userName,
            passwordHash = passwordHash,
            greeting = greeting,
            isAdmin = isAdmin,
            localeTag = locale.toLanguageTag(),
            languageCode = languageCode
        )
    }
}
