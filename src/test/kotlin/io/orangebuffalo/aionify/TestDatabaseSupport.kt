package io.orangebuffalo.aionify

import io.micronaut.context.ApplicationContext
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.annotation.Transactional
import io.orangebuffalo.aionify.domain.ActivationToken
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Singleton
import javax.sql.DataSource

/**
 * Support class for database operations in tests.
 * Provides utilities for database cleanup and setup.
 * 
 * **CRITICAL:** All write operations are wrapped in transactions to ensure
 * they are committed immediately and visible to other connections (e.g., browser HTTP requests).
 */
@Singleton
open class TestDatabaseSupport(
    private val dataSource: DataSource,
    private val applicationContext: ApplicationContext,
    private val genericRepository: GenericEntityRepository
) {

    /**
     * Truncates all application tables, resetting the database to a clean state.
     * Tables are truncated in order to respect foreign key constraints.
     * The flyway_schema_history table is preserved to maintain migration history.
     * 
     * This operation is wrapped in a NEW transaction to ensure it commits immediately
     * and doesn't interfere with test transactions.
     */
    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    open fun truncateAllTables() {
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
    
    /**
     * Executes the given block in a new transaction and commits it.
     * Use this for any database write operations in tests to ensure they are
     * committed and visible to other connections (e.g., browser HTTP requests).
     * 
     * Example:
     * ```
     * val user = testDatabaseSupport.inTransaction {
     *     genericRepository.save(User.create(...))
     * }
     * ```
     */
    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    open fun <T> inTransaction(block: () -> T): T {
        return block()
    }
    
    /**
     * Inserts an entity in a new transaction and returns it (potentially with generated ID).
     * The transaction is committed immediately, making the entity visible to other connections.
     * 
     * Example:
     * ```
     * val user = testDatabaseSupport.insert(User.create(...))
     * ```
     */
    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    open fun <T : Any> insert(entity: T): T {
        @Suppress("UNCHECKED_CAST")
        return when (entity) {
            is User -> genericRepository.save(entity) as T
            is TimeLogEntry -> genericRepository.save(entity) as T
            is ActivationToken -> genericRepository.save(entity) as T
            else -> throw IllegalArgumentException("Unsupported entity type: ${entity::class.java.name}")
        }
    }
    
    /**
     * Updates an entity in a new transaction and returns it.
     * The transaction is committed immediately, making the changes visible to other connections.
     * 
     * Example:
     * ```
     * val updatedUser = testDatabaseSupport.update(user.copy(greeting = "New Greeting"))
     * ```
     */
    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    open fun <T : Any> update(entity: T): T {
        @Suppress("UNCHECKED_CAST")
        return when (entity) {
            is User -> genericRepository.update(entity) as T
            is TimeLogEntry -> genericRepository.update(entity) as T
            is ActivationToken -> genericRepository.update(entity) as T
            else -> throw IllegalArgumentException("Unsupported entity type: ${entity::class.java.name}")
        }
    }
}

