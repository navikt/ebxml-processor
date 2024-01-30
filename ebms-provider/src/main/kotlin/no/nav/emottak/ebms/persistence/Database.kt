package no.nav.emottak.ebms.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

class Database(
    dbConfig: HikariConfig
) {
    val dataSource by lazy { HikariDataSource(dbConfig) }
    val db by lazy { Database.connect(dataSource) }
    private val config = dbConfig
    fun migrate() {
        migrationConfig(config)
            .let(::HikariDataSource)
            .also {
                Flyway.configure()
                    .dataSource(it)
                    .lockRetryCount(50)
                    .load()
                    .migrate()
            }.close()
    }

    private fun migrationConfig(conf: HikariConfig): HikariConfig =
        HikariConfig().apply {
            jdbcUrl = conf.jdbcUrl
            username = conf.username
            password = conf.password
            maximumPoolSize = 3
        }
}
