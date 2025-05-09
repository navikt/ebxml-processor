package no.nav.emottak.cpa.persistence

import com.bettercloud.vault.response.LogicalResponse
import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.cpa.log
import no.nav.emottak.utils.environment.fromEnv
import no.nav.emottak.utils.environment.getEnvVar
import no.nav.emottak.utils.vault.VaultUser
import no.nav.emottak.utils.vault.VaultUtil
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import java.io.FileInputStream

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
            val path: String = this@configure.vaultMountPath + "/creds/$databaseName-$role"
            log.info("Fetching database credentials for role admin ($path)")
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

data class OracleDBConfig(
    private val vaultUser: VaultUser = getVaultServiceUser(),
    val username: String = vaultUser.username,
    val password: String = vaultUser.password,
    val url: String = getVaultJdbcUrl()
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

private fun getVaultServiceUser(): VaultUser {
    val localPath = "/secrets/oracle/creds"
    return try {
        VaultUser(
            String(FileInputStream("$localPath/username").readAllBytes()),
            String(FileInputStream("$localPath/password").readAllBytes())
        ).also { log.info("Vault credential secret read from local filesystem") }
    } catch (e: java.io.FileNotFoundException) {
        VaultUtil.getVaultServiceUser("ORACLE_CREDENTIAL_VAULT_PATH", "/oracle/data/dev/creds/emottak_q1-nmt3")
    }
}

private fun getVaultJdbcUrl(): String {
    val localPath = "/secrets/oracle/config"
    return try {
        String(FileInputStream("$localPath/username").readAllBytes()).also { log.info("Vault jdbc_url secret read from local filesystem") }
    } catch (e: java.io.FileNotFoundException) {
        VaultUtil.readVaultPathResource("ORACLE_CONFIG_VAULT_PATH".fromEnv(), "jdbc_url")
    }
}
