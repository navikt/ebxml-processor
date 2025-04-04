package no.nav.emottak.crypto

import no.nav.emottak.utils.vault.VaultUtil
import no.nav.emottak.utils.vault.parseVaultJsonObject
import java.io.InputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.encoding.decodingWith

class VaultKeyStoreConfig(
    keyStoreVaultPath: String,
    keyStoreFileResource: String,
    keyStorePassResource: String
) : KeyStoreConfig {
    private val keystoreVaultMap: Map<String, String> = VaultUtil.readVaultPathData(keyStoreVaultPath)
    override val keyStoreFile: InputStream = keystoreVaultMap.getDecodedVaultKeyStoreFile(keyStoreFileResource)
    override val keyStorePass: CharArray = keystoreVaultMap[keyStorePassResource]!!.parseVaultJsonObject("password").toCharArray()
    override val keyStoreType: String = keystoreVaultMap[keyStorePassResource]!!.parseVaultJsonObject("type")

    @OptIn(ExperimentalEncodingApi::class)
    private fun Map<String, String>.getDecodedVaultKeyStoreFile(keyStoreFileResource: String): InputStream =
        this[keyStoreFileResource]!!.byteInputStream().decodingWith(Base64.Mime)
}
