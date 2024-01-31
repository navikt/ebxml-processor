package no.nav.emottak.cpa.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

class Database(
    dbConfig: HikariConfig
) {
    val dataSource = HikariDataSource(dbConfig)
    val db = Database.connect(dataSource)
    fun migrate(dbConfig: HikariConfig) {
        dbConfig.let(::HikariDataSource)
            .also {
                Flyway.configure()
                    .dataSource(it)
                    .initSql("SET ROLE \"$database_name-admin\"")
                    .lockRetryCount(50)
                    .load()
                    .migrate()
            }.close()
    }
}
