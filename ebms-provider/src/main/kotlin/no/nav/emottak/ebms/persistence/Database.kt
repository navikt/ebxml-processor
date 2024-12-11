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
            .locations("filesystem:src/main/resources/db/migrations")
            .lockRetryCount(50)
            .cleanDisabled(false) // TODO: Remove before merging.
            .load()
            .also(Flyway::clean) // TODO: Remove before merging. So unfortunate, if you are seeing this in main.
            .migrate()
    }
}
