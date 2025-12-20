package io.orangebuffalo.aionify

import io.micronaut.data.annotation.Repository
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.orangebuffalo.aionify.domain.ActivationToken
import io.orangebuffalo.aionify.domain.TimeEntry
import io.orangebuffalo.aionify.domain.User

/**
 * Generic repository for test purposes that can insert/update any entity.
 * This allows tests to avoid direct dependencies on specific entity repositories.
 * 
 * All operations should be wrapped in TestDatabaseSupport methods to ensure
 * proper transaction handling and visibility to HTTP requests.
 */
@Repository
@JdbcRepository(dialect = Dialect.POSTGRES)
interface GenericEntityRepository {
    
    /**
     * Inserts/updates a User entity.
     */
    fun save(entity: User): User
    
    /**
     * Inserts/updates a TimeEntry entity.
     */
    fun save(entity: TimeEntry): TimeEntry
    
    /**
     * Inserts/updates an ActivationToken entity.
     */
    fun save(entity: ActivationToken): ActivationToken
    
    /**
     * Updates a User entity.
     */
    fun update(entity: User): User
    
    /**
     * Updates a TimeEntry entity.
     */
    fun update(entity: TimeEntry): TimeEntry
    
    /**
     * Updates an ActivationToken entity.
     */
    fun update(entity: ActivationToken): ActivationToken
}
