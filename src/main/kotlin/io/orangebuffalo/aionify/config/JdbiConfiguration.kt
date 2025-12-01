package io.orangebuffalo.aionify.config

import io.agroal.api.AgroalDataSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin

@ApplicationScoped
class JdbiConfiguration(private val dataSource: AgroalDataSource) {

    @Produces
    @Singleton
    fun jdbi(): Jdbi {
        return Jdbi.create(dataSource).apply {
            installPlugin(SqlObjectPlugin())
            installPlugin(KotlinPlugin())
        }
    }
}
