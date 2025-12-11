package io.orangebuffalo.aionify

import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton

/**
 * Helper service to execute database operations in a separate transaction.
 * This ensures that changes are committed and visible to other connections (e.g., HTTP requests from browser).
 *
 * Use this when you need to ensure data is persisted before browser interactions in Playwright tests.
 */
@Singleton
open class TestTransactionHelper {

    /**
     * Executes the given block in a new transaction and commits it.
     * The transaction is committed when this method returns, making changes visible to other connections.
     */
    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    open fun <T> inTransaction(block: () -> T): T {
        return block()
    }
}

