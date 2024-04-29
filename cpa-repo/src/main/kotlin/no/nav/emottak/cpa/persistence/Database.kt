package no.nav.emottak.cpa.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

class Database(
    dbConfig: HikariConfig
) {
    val dataSource = when (dbConfig) {
        is HikariDataSource -> dbConfig
        else -> HikariDataSource(dbConfig)
    }
    val db = Database.connect(dataSource)
    fun migrate(migrationConfig: HikariConfig) {
        migrationConfig.let(::HikariDataSource)
            .also {
                Flyway.configure()
                    .dataSource(it)
                    .initSql("SET ROLE \"$CPA_DB_NAME-admin\"")
                    .lockRetryCount(50)
                    .load()
                    .migrate()
            }.close()
    }
}
