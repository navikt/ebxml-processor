package no.nav.emottak.ebms.async.persistence

import com.bettercloud.vault.response.LogicalResponse
import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.ebms.async.log
import no.nav.emottak.utils.environment.getEnvVar
import no.nav.emottak.utils.vault.VaultUtil
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil

const val EBMS_DB_NAME = "emottak-ebms-db"

val ebmsDbConfig = lazy { VaultConfig().configure("user") } // TODO: Opprette configurasjon for lokal database

val ebmsMigrationConfig = lazy { VaultConfig().configure("admin") }

data class VaultConfig(
    val databaseName: String = EBMS_DB_NAME,
    val jdbcUrl: String = getEnvVar("VAULT_JDBC_URL", "jdbc:postgresql://b27dbvl033.preprod.local:5432/").also {
        log.info("vault jdbc url set til: $it")
    },
    val vaultMountPath: String = ("postgresql/prod-fss".takeIf { getEnvVar("NAIS_CLUSTER_NAME", "local") == "prod-fss" } ?: "postgresql/preprod-fss").also {
        log.info("vaultMountPath satt til $it")
    }
)

fun VaultConfig.configure(role: String): HikariConfig {
    val maxPoolSizeForUser = getEnvVar("MAX_CONNECTION_POOL_SIZE_FOR_USER", "4").toInt()
    val maxPoolSizeForAdmin = getEnvVar("MAX_CONNECTION_POOL_SIZE_FOR_ADMIN", "1").toInt()

    val hikariConfig = HikariConfig().apply {
        jdbcUrl = this@configure.jdbcUrl + databaseName
        driverClassName = "org.postgresql.Driver"
        this.maximumPoolSize = maxPoolSizeForUser
        if (role == "admin") {
            this.maximumPoolSize = maxPoolSizeForAdmin
            val path: String = this@configure.vaultMountPath + "/creds/$databaseName-$role"
            log.info("Fetching database credentials for role admin")
            val response: LogicalResponse = VaultUtil.getClient().logical().read(path)
            this.username = response.data["username"]
            this.password = response.data["password"]
        }
    }

    if (role == "admin") {
        return hikariConfig
    }
    return HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
        hikariConfig,
        this@configure.vaultMountPath,
        "$databaseName-$role"
    )
}
