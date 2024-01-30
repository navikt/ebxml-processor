package no.nav.emottak.cpa

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
                    .lockRetryCount(50)
                    .load()
                    .migrate()
            }.close()
    }
}
