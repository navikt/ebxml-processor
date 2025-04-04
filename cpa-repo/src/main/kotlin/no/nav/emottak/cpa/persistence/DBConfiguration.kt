package no.nav.emottak.cpa.persistence

import com.bettercloud.vault.response.LogicalResponse
import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.cpa.log
import no.nav.emottak.utils.environment.fromEnv
import no.nav.emottak.utils.environment.getEnvVar
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import no.nav.vault.jdbc.hikaricp.VaultUtil

const val CPA_DB_NAME = "emottak-cpa-repo-db"

private val cluster = getEnvVar("NAIS_CLUSTER_NAME")

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
