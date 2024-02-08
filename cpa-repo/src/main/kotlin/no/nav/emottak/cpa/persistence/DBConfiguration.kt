package no.nav.emottak.cpa.persistence

import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.util.fromEnv
import no.nav.emottak.util.getEnvVar
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil

const val CPA_DB_NAME = "emottak-cpa-repo-db"

private val cluster = System.getenv("NAIS_CLUSTER_NAME")

val cpaDbConfig = lazy {
    when (cluster) {
        "dev-fss" -> VaultConfig().configure("user")
        "dev-gcp" -> VaultConfig().configure("user")
        else -> EnvDBConfig().configure()
    }
}
val cpaMigrationConfig = lazy {
    when (cluster) {
        "dev-fss" -> VaultConfig().configure("admin")
        "dev-gcp" -> VaultConfig().configure("admin")
        else -> EnvDBConfig().configure()
    }
}

val oracleConfig = lazy {
    EnvDBConfig(
        url = "ORACLE_URL".fromEnv(),
        username = "ORACLE_USERNAME".fromEnv(),
        password = "ORACLE_PASSWORD".fromEnv()
    ).configure()
}

private const val prefix = "NAIS_DATABASE_CPA_REPO_CPA_REPO_DB"

data class VaultConfig(
    val databaseName: String = CPA_DB_NAME,
    val jdbcUrl: String = getEnvVar("VAULT_JDBC_URL", "jdbc:postgresql://b27dbvl033.preprod.local:5432/"),
    val vaultMountPath: String = "postgresql/preprod-fss"
)

fun VaultConfig.configure(role: String): HikariConfig {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = "$this@configure.jdbcUrl$databaseName"
    }
    return HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
        hikariConfig,
        this@configure.vaultMountPath,
        "$databaseName-$role"
    )
}

data class EnvDBConfig(
    val host: String = "${prefix}_HOST".fromEnv(),
    val port: String = "${prefix}_PORT".fromEnv(),
    val name: String = "${prefix}_DATABASE".fromEnv(),
    val username: String = "${prefix}_USERNAME".fromEnv(),
    val password: String = "${prefix}_PASSWORD".fromEnv(),
    val url: String = "jdbc:postgresql://%s:%s/%s".format(host, port, name)
)

fun EnvDBConfig.configure(): HikariConfig {
    return HikariConfig().apply {
        jdbcUrl = this@configure.url
        username = this@configure.username
        password = this@configure.password
    }
}
