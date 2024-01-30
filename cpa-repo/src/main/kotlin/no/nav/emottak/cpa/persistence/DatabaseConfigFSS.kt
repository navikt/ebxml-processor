package no.nav.emottak.cpa.persistence

import com.zaxxer.hikari.HikariConfig
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil

private const val database_name = "emottak-cpa-repo-db"

data class DatabaseConfigFSS(
    val mountPath: String,
    val jdbcUrl: String
)

fun mapHikariConfig(role: String, dbConfig: HikariConfig? = null): HikariConfig {
    return if (Environment.isFSS) {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            minimumIdle = 1
            maximumPoolSize = 2
            driverClassName = "org.postgresql.Driver"
        }
        HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig,
            config.mountPath,
            "$database_name-$role"
        )
    } else {
        dbConfig ?: throw RuntimeException("Only FSS or custom hikari config supported")
    }
}

private val config: DatabaseConfigFSS = when (Environment.current) {
    Environment.DEV_FSS -> DatabaseConfigFSS(
        mountPath = "postgresql/preprod-fss",
        jdbcUrl = "jdbc:postgresql://b27dbvl033.preprod.local:5432/$database_name"
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
