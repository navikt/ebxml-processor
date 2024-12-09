package no.nav.emottak.ebms.persistence

import com.bettercloud.vault.response.LogicalResponse
import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.ebms.log
import no.nav.emottak.util.getEnvVar
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import no.nav.vault.jdbc.hikaricp.VaultUtil

const val EBMS_DB_NAME = "emottak-ebms-db"

private val cluster = getEnvVar("NAIS_CLUSTER_NAME")

val ebmsDbConfig = lazy {
    when (cluster) {
        "dev-fss" -> VaultConfig().configure("user")
        "prod-fss" -> VaultConfig().configure("user")
        // TODO: Opprette configurasjon for lokal database
        else -> VaultConfig().configure("user")
    }
}
val ebmsMigrationConfig = lazy {
    when (cluster) {
        "dev-fss" -> VaultConfig().configure("admin")
        "prod-fss" -> VaultConfig().configure("admin")
        // TODO: Opprette configurasjon for lokal database
        else -> VaultConfig().configure("admin")
    }
}

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
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = this@configure.jdbcUrl + databaseName
        driverClassName = "org.postgresql.Driver"
        if (role == "admin") {
            this.maximumPoolSize = 2
            val vault = VaultUtil.getInstance().client
            val path: String = this@configure.vaultMountPath + "/creds/$databaseName-$role"
            log.info("Fetching database credentials for role admin")
            val response: LogicalResponse = vault.logical().read(path)
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
