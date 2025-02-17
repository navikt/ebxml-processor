package no.nav.emottak.payload.ocspstatus

import no.nav.emottak.crypto.FileKeyStoreConfig
import no.nav.emottak.crypto.parseVaultJsonObject
import no.nav.emottak.util.getEnvVar
import java.io.FileReader

internal fun trustStoreConfig() = FileKeyStoreConfig(
    keyStoreFilePath = getEnvVar("TRUSTSTORE_PATH", resolveDefaultTruststorePath()),
    keyStorePass = getEnvVar("TRUSTSTORE_PWD", "123456789").toCharArray(),
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

fun ocspSigneringConfigBuypass() =
    when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
        "dev-fss", "prod-fss" ->
            FileKeyStoreConfig(
                keyStoreFilePath = getEnvVar("KEYSTORE_BUYPASS_STORE"),
                keyStorePass = getEnvVar("KEYSTORE_BUYPASS_PWD").toCharArray(),
                keyStoreType = getEnvVar("KEYSTORE_BUYPASS_TYPE", "PKCS12")
            )
        else ->
            FileKeyStoreConfig(
                keyStoreFilePath = getEnvVar("KEYSTORE_FILE_SIGN", "keystore/test_keystore2024.p12"),
                keyStorePass = FileReader(
                    getEnvVar(
                        "KEYSTORE_PWD_FILE",
                        FileKeyStoreConfig::class.java.classLoader.getResource("keystore/credentials-test.json")?.path.toString()
                    )
                ).readText().parseVaultJsonObject("password").toCharArray(),
                keyStoreType = getEnvVar("KEYSTORE_TYPE", "PKCS12")
            )
    }
