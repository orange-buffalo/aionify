package io.orangebuffalo.aionify.domain

import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.runtime.convert.AttributeConverter
import jakarta.inject.Singleton

@Singleton
class WeeklyWorkingDaysConverter : AttributeConverter<Set<WeekDay>, String> {
    override fun convertToPersistedValue(
        entityValue: Set<WeekDay>?,
        context: ConversionContext,
    ): String = entityValue.orEmpty().sortedBy { it.ordinal }.joinToString(",") { it.name }

    override fun convertToEntityValue(
        persistedValue: String?,
        context: ConversionContext,
    ): Set<WeekDay> =
        persistedValue
            .orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .mapTo(linkedSetOf()) { WeekDay.valueOf(it) }
}
