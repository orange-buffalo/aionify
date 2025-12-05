package io.orangebuffalo.aionify

import jakarta.enterprise.context.ApplicationScoped
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.mapTo

/**
 * Support class for database operations in tests.
 * Provides utilities for database cleanup and setup.
 */
@ApplicationScoped
class TestDatabaseSupport(private val jdbi: Jdbi) {

    /**
     * Truncates all application tables, resetting the database to a clean state.
     * Tables are truncated in order to respect foreign key constraints.
     * The flyway_schema_history table is preserved to maintain migration history.
     */
    fun truncateAllTables() {
        jdbi.useHandle<Exception> { handle ->
            // Get all table names from the public schema, excluding Flyway's metadata table
            val tables = handle.createQuery(
                """
                SELECT tablename FROM pg_tables 
                WHERE schemaname = 'public' 
                AND tablename != 'flyway_schema_history'
                ORDER BY tablename
                """.trimIndent()
            )
                .mapTo<String>()
                .list()

            // Truncate all tables with CASCADE to handle any foreign key constraints
            if (tables.isNotEmpty()) {
                val tableList = tables.joinToString(", ") { "\"$it\"" }
                handle.execute("TRUNCATE TABLE $tableList CASCADE")
            }
        }
    }
}
