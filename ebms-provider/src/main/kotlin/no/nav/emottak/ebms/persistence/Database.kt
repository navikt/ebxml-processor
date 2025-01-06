package no.nav.emottak.ebms.persistence

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
        Flyway.configure()
            .dataSource(migrationConfig.jdbcUrl, migrationConfig.username, migrationConfig.password)
            .initSql("SET ROLE \"$EBMS_DB_NAME-admin\"")
            .lockRetryCount(50)
//            .also {
//                if (getEnvVar("NAIS_CLUSTER_NAME", "local") == "local") {
//                    it.locations("filesystem:src/main/resources/db/migrations")
//                }
//            }
            .load()
            .migrate()
    }
}
