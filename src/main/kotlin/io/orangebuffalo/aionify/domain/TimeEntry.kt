package io.orangebuffalo.aionify.domain

import java.time.Instant

data class TimeEntry(
    val id: Long? = null,
    val startTime: Instant
)
