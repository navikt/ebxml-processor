package no.nav.emottak.util

import no.nav.emottak.crypto.FileKeyStoreConfig
import no.nav.emottak.crypto.VaultKeyStoreConfig

data class KeyStoreConfiguration(
    val type: ConfigurationType,
    val path: String,
    val fileResource: String,
    val passResource: String,
    val keyStoreType: String
) {
    fun resolveKeyStoreConfiguration() =
        when (this.type) {
            ConfigurationType.VAULT -> VaultKeyStoreConfig(
                keyStoreVaultPath = this.path,
                keyStoreFileResource = this.fileResource,
                keyStorePassResource = this.passResource
            )
            ConfigurationType.FILE -> FileKeyStoreConfig(
                keyStoreFilePath = this.fileResource,
                keyStorePass = this.passResource.toCharArray(),
                keyStoreType = this.keyStoreType
            )
        }
}

enum class ConfigurationType {
    VAULT, FILE
}
