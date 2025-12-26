package io.orangebuffalo.aionify.domain

import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.runtime.convert.AttributeConverter
import jakarta.inject.Singleton

/**
 * Converts between List<String> (Kotlin) and TEXT[] (PostgreSQL) for tag arrays.
 */
@Singleton
class TagsConverter : AttributeConverter<List<String>, Any> {
    
    override fun convertToPersistedValue(entityValue: List<String>?, context: ConversionContext?): Any? {
        return entityValue?.toTypedArray()
    }
    
    override fun convertToEntityValue(persistedValue: Any?, context: ConversionContext?): List<String>? {
        // PostgreSQL JDBC driver returns java.sql.Array (PgArray) when reading array columns
        // We need to extract the actual array from it
        if (persistedValue is java.sql.Array) {
            @Suppress("UNCHECKED_CAST")
            val array = persistedValue.array as? kotlin.Array<*>
            return array?.filterIsInstance<String>() ?: emptyList()
        }
        // Handle case when it's already an array
        if (persistedValue is kotlin.Array<*>) {
            return persistedValue.filterIsInstance<String>()
        }
        return emptyList()
    }
}
