package no.nav.emottak.payload.ocspstatus

import no.nav.emottak.crypto.FileKeyStoreConfig
import no.nav.emottak.crypto.parseVaultJsonObject
import no.nav.emottak.payload.crypto.payloadSigneringConfig
import no.nav.emottak.util.getEnvVar
import java.io.FileReader

internal fun trustStoreConfig() = FileKeyStoreConfig(
    keyStoreFilePath = getEnvVar("TRUSTSTORE_PATH", resolveDefaultTruststorePath()),
    keyStorePass = getEnvVar("TRUSTSTORE_PWD", "changeit").toCharArray(),
    keyStoreType = "PKCS12"
)

fun ocspSigneringConfigCommfides() =
    when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
        "dev-fss", "prod-fss" -> FileKeyStoreConfig(
            keyStoreFilePath = getEnvVar("KEYSTORE_COMMFIDES_STORE"),
            keyStorePass = getEnvVar("KEYSTORE_COMMFIDES_PWD").toCharArray(),
            keyStoreType = getEnvVar("KEYSTORE_COMMFIDES_TYPE", "PKCS12")
        )
        else -> {
            FileKeyStoreConfig(
                keyStoreFilePath = getEnvVar("KEYSTORE_COMMFIDES_STORE", "keystore/test_keystore2024.p12"),
                keyStorePass = FileReader(
                    getEnvVar(
                        "KEYSTORE_COMMFIDES_PWD",
                        FileKeyStoreConfig::class.java.classLoader.getResource("keystore/credentials-test.json")?.path.toString()
                    )
                ).readText().parseVaultJsonObject("password").toCharArray(),
                keyStoreType = getEnvVar("KEYSTORE_COMMFIDES_TYPE", "PKCS12")
            )
        }
    }

fun ocspSigneringConfigBuypass() = payloadSigneringConfig() // TODO split this
