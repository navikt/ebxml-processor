package no.nav.emottak.cpa.persistence

import com.zaxxer.hikari.HikariConfig
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil

const val CPA_DB_NAME = "emottak-cpa-repo-db"

data class DatabaseConfigFSS(
    val mountPath: String,
    val jdbcUrl: String
)

fun mapHikariConfig(databaseName: String, role: String): HikariConfig {

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            minimumIdle = 1
            maximumPoolSize = 2
            driverClassName = "org.postgresql.Driver"
        }
       return HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig,
            config.mountPath,
            "$databaseName-$role"
        )

}

private val config: DatabaseConfigFSS = when (Environment.current) {
    Environment.DEV_FSS -> DatabaseConfigFSS(
        mountPath = "postgresql/preprod-fss",
        jdbcUrl = "jdbc:postgresql://b27dbvl033.preprod.local:5432/$CPA_DB_NAME"
    )
    Environment.PROD_FSS -> throw TODO()
    Environment.LOKAL -> DatabaseConfigFSS(
        mountPath = "",
        jdbcUrl = ""
    )
}

enum class Environment {
    DEV_FSS, PROD_FSS, LOKAL;

    companion object {
        private val env: String? = System.getenv("NAIS_CLUSTER_NAME")
        val current: Environment = when (env) {
            "dev-fss" -> DEV_FSS
            "prod-fss" -> PROD_FSS
            null -> LOKAL
            else -> throw RuntimeException("Ukjent cluster: $env")
        }
        val isFSS: Boolean = when (env) {
            "dev-fss" -> true
            "prod-fss" -> true
            else -> false
        }
    }
}
