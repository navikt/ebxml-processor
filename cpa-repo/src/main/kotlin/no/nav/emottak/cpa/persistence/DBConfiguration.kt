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
        "dev-gcp" -> GcpDBConfig().configure()
        "prod-fss" -> VaultConfig().configure("user")
        else -> GcpDBConfig().configure()
    }
}
val cpaMigrationConfig = lazy {
    when (cluster) {
        "dev-fss" -> VaultConfig().configure("admin")
        "dev-gcp" -> GcpDBConfig().configure()
        "prod-fss" -> VaultConfig().configure("admin")
        else -> GcpDBConfig().configure()
    }
}

val oracleConfig = lazy {
    OracleDBConfig().configure()
}

private const val prefix = "NAIS_DATABASE_CPA_REPO_CPA_REPO_DB"

data class VaultConfig(
    val databaseName: String = CPA_DB_NAME,
    val jdbcUrl: String = getEnvVar("VAULT_JDBC_URL", "jdbc:postgresql://b27dbvl033.preprod.local:5432/"),
    val vaultMountPath: String = "postgresql/preprod-fss"
)

fun VaultConfig.configure(role: String): HikariConfig {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = this@configure.jdbcUrl + databaseName
        driverClassName = "org.postgresql.Driver"
    }
    return HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
        hikariConfig,
        this@configure.vaultMountPath,
        "$databaseName-$role"
    )
}

data class OracleDBConfig(
    val username: String = "EMOTTAK_USERNAME".fromEnv(),
    val password: String = "EMOTTAK_PASSWORD".fromEnv(),
    val url: String = "EMOTTAK_JDBC_URL".fromEnv()
)

fun OracleDBConfig.configure(): HikariConfig {
    return HikariConfig().apply {
        jdbcUrl = this@configure.url
        username = this@configure.username
        password = this@configure.password
        driverClassName = "oracle.jdbc.OracleDriver"
    }
}

data class GcpDBConfig(
    val host: String = "${prefix}_HOST".fromEnv(),
    val port: String = "${prefix}_PORT".fromEnv(),
    val name: String = "${prefix}_DATABASE".fromEnv(),
    val username: String = "${prefix}_USERNAME".fromEnv(),
    val password: String = "${prefix}_PASSWORD".fromEnv(),
    val url: String = "jdbc:postgresql://%s:%s/%s".format(host, port, name)
)

fun GcpDBConfig.configure(): HikariConfig {
    return HikariConfig().apply {
        jdbcUrl = this@configure.url
        username = this@configure.username
        password = this@configure.password
    }
}
