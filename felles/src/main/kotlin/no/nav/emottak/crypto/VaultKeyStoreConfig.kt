package no.nav.emottak.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.emottak.vault.VaultUtil
import java.io.InputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.encoding.decodingWith

class VaultKeyStoreConfig(
    keyStoreVaultPath: String,
    keyStoreFileResource: String,
    keyStorePassResource: String
): KeyStoreConfig {
    override val keyStoreFile: InputStream = getDecodedVaultKeyStoreFile(keyStoreVaultPath, keyStoreFileResource)
    override val keyStorePass: CharArray = VaultUtil.readVaultPathResource(keyStoreVaultPath, keyStorePassResource).parseVaultJsonObject("password").toCharArray()
    override val keyStoreType: String = VaultUtil.readVaultPathResource(keyStoreVaultPath, keyStorePassResource).parseVaultJsonObject("type")

    @OptIn(ExperimentalEncodingApi::class)
    private fun getDecodedVaultKeyStoreFile(keyStoreVaultPath: String, keyStoreFileResource: String): InputStream =
        VaultUtil.readVaultPathResource(keyStoreVaultPath, keyStoreFileResource).byteInputStream().decodingWith(Base64.Mime)
}

fun String.parseVaultJsonObject(field: String) = Json.parseToJsonElement(
    this
).jsonObject[field]!!.jsonPrimitive.content
