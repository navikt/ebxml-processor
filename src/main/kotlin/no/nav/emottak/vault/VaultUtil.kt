package no.nav.emottak.vault

import com.bettercloud.vault.SslConfig
import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.VaultException
import com.bettercloud.vault.response.AuthResponse
import com.bettercloud.vault.response.LookupResponse
import no.nav.emottak.util.getEnvVar
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Timer
import java.util.TimerTask


class VaultUtil private constructor() {
    private val client: Vault
    private val timer: Timer = Timer("VaultScheduler", true)

    init {
        val vaultConfig = try {
            VaultConfig()
                .address(getEnvVar("VAULT_ADDR", "https://vault.adeo.no"))
                .token(vaultToken)
                .openTimeout(5)
                .readTimeout(30)
                .sslConfig(SslConfig().build())
                .build()
        } catch (e: VaultException) {
            throw RuntimeException("Could not instantiate the Vault REST client", e)
        }
        client = Vault(vaultConfig, 1)

        // Verify that the token is ok
        val lookupSelf: LookupResponse
        try {
            lookupSelf = client.auth().lookupSelf()
        } catch (e: VaultException) {
            if (e.httpStatusCode == 403) {
                throw RuntimeException("The application's vault token seems to be invalid", e)
            } else {
                throw RuntimeException("Could not validate the application's vault token", e)
            }
        }

        if (lookupSelf.isRenewable) {
            class RefreshTokenTask : TimerTask() {
                override fun run() {
                    try {
                        logger.info("Refreshing Vault token (old TTL = ${client.auth().lookupSelf().ttl} seconds)")
                        val response: AuthResponse = client.auth().renewSelf()
                        logger.info("Refreshed Vault token (new TTL = ${client.auth().lookupSelf().ttl} seconds)")
                        timer.schedule(
                            RefreshTokenTask(),
                            suggestedRefreshInterval(response.authLeaseDuration * 1000)
                        )
                    } catch (e: VaultException) {
                        logger.error("Could not refresh the Vault token", e)
                        logger.warn("Waiting 5 secs before trying to refresh the Vault token")
                        timer.schedule(RefreshTokenTask(), 5000)
                    }
                }
            }
            logger.info("Starting a refresh timer on the vault token (TTL = ${lookupSelf.ttl} seconds)")
            timer.schedule(RefreshTokenTask(), suggestedRefreshInterval(lookupSelf.ttl * 1000))
        } else {
            logger.warn("Vault token is not renewable")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(VaultUtil::class.java)

        private const val VAULT_TOKEN_PROPERTY: String = "VAULT_TOKEN"
        private const val VAULT_TOKEN_PATH_PROPERTY: String = "VAULT_TOKEN_PATH"
        private const val MIN_REFRESH_MARGIN: Int = 10 * 60 * 1000 // 10 min in ms;

        private val instance: VaultUtil = VaultUtil()

        fun readVaultPathResource(path: String, resource: String): String =
            instance.client.logical().read(path).data[resource].also {
                logger.info("Got vault resource $resource from vault path $path")
            } ?: throw RuntimeException("Failed to read vault path resource: $path/$resource")

        // We should refresh tokens from Vault before they expire, so we add a MIN_REFRESH_MARGIN margin.
        // If the token is valid for less than MIN_REFRESH_MARGIN * 2, we use duration / 2 instead.
        private fun suggestedRefreshInterval(duration: Long): Long {
            return if (duration < MIN_REFRESH_MARGIN * 2) {
                duration / 2
            } else {
                duration - MIN_REFRESH_MARGIN
            }
        }

        private fun getProperty(propertyName: String): String? {
            return System.getProperty(propertyName, System.getenv(propertyName))
        }

        private val vaultToken: String
            get() {
                try {
                    if (!getProperty(VAULT_TOKEN_PROPERTY).isNullOrBlank()) {
                        return getEnvVar(VAULT_TOKEN_PROPERTY)
                    } else if (!getProperty(VAULT_TOKEN_PATH_PROPERTY).isNullOrBlank()) {
                        val encoded: ByteArray = Files.readAllBytes(Paths.get(getEnvVar(VAULT_TOKEN_PATH_PROPERTY)))
                        return String(encoded, charset("UTF-8")).trim { it <= ' ' }
                    } else if (Files.exists(Paths.get("/var/run/secrets/nais.io/vault/vault_token"))) {
                        val encoded: ByteArray =
                            Files.readAllBytes(Paths.get("/var/run/secrets/nais.io/vault/vault_token"))
                        return String(encoded, charset("UTF-8")).trim { it <= ' ' }
                    } else {
                        throw RuntimeException("Neither $VAULT_TOKEN_PROPERTY or $VAULT_TOKEN_PATH_PROPERTY is set")
                    }
                } catch (e: Exception) {
                    throw RuntimeException("Could not get a vault token for authentication", e)
                }
            }
    }
}
