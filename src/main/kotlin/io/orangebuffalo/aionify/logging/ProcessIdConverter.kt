package io.orangebuffalo.aionify.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import io.micronaut.core.annotation.Introspected

/**
 * Logback converter that outputs the current process ID.
 * Usage in logback.xml: <conversionRule conversionWord="pid" converterClass="io.orangebuffalo.aionify.logging.ProcessIdConverter" />
 * Then use %pid in the pattern.
 */
@Introspected
class ProcessIdConverter : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String = ProcessHandle.current().pid().toString()
}
