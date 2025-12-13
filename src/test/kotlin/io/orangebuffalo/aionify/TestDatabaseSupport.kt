package io.orangebuffalo.aionify

import io.micronaut.context.ApplicationContext
import jakarta.inject.Singleton
import javax.sql.DataSource

/**
 * Support class for database operations in tests.
 * Provides utilities for database cleanup and setup.
 */
@Singleton
class TestDatabaseSupport(
    private val dataSource: DataSource,
    private val applicationContext: ApplicationContext
) {

    /**
     * Truncates all application tables, resetting the database to a clean state.
     * Tables are truncated in order to respect foreign key constraints.
     * The flyway_schema_history table is preserved to maintain migration history.
     */
    fun truncateAllTables() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                // Get all table names from the public schema, excluding Flyway's metadata table
                val tables = mutableListOf<String>()
                statement.executeQuery(
                    """
                    SELECT tablename FROM pg_tables 
                    WHERE schemaname = 'public' 
                    AND tablename != 'flyway_schema_history'
                    ORDER BY tablename
                    """.trimIndent()
                ).use { rs ->
                    while (rs.next()) {
                        tables.add(rs.getString(1))
                    }
                }

                // Truncate all tables with CASCADE to handle any foreign key constraints
                if (tables.isNotEmpty()) {
                    val tableList = tables.joinToString(", ") { "\"$it\"" }
                    statement.execute("TRUNCATE TABLE $tableList CASCADE")
                }
            }
        }
    }
}
