package io.orangebuffalo.aionify

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.ApplicationContextConfigurer
import org.postgresql.ds.PGSimpleDataSource
import java.util.UUID

class TestPostgresConfigurer : ApplicationContextConfigurer {
    override fun configure(builder: ApplicationContextBuilder) {
        val serviceUrl = System.getenv("AIONIFY_TEST_POSTGRES_URL") ?: return

        // Share the PostgreSQL server, not the database. Flyway still migrates a fresh public schema.
        val databaseName = "test_${UUID.randomUUID().toString().replace("-", "")}"
        val dataSource = PGSimpleDataSource().apply { setURL(serviceUrl) }
        dataSource.getConnection("test", "test").use { connection ->
            connection.createStatement().use { it.executeUpdate("CREATE DATABASE $databaseName") }
        }
        dataSource.databaseName = databaseName

        builder.properties(
            mapOf(
                "datasources.default.url" to dataSource.getURL(),
                "datasources.default.driver-class-name" to "org.postgresql.Driver",
            ),
        )
    }
}
