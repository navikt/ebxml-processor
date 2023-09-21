package no.nav.emottak.util.signatur

import java.security.Key
import java.security.KeyException
import java.security.PublicKey
import java.security.cert.X509Certificate
import javax.xml.crypto.AlgorithmMethod
import javax.xml.crypto.KeySelector
import javax.xml.crypto.KeySelectorException
import javax.xml.crypto.KeySelectorResult
import javax.xml.crypto.XMLCryptoContext
import javax.xml.crypto.XMLStructure
import javax.xml.crypto.dsig.SignatureMethod
import javax.xml.crypto.dsig.keyinfo.KeyInfo
import javax.xml.crypto.dsig.keyinfo.X509Data

class KeyValueKeySelector: KeySelector() {

    override fun select(
        keyInfo: KeyInfo,
        purpose: Purpose?,
        method: AlgorithmMethod,
        context: XMLCryptoContext?
    ): KeySelectorResult {
        val signatureMethod = method as SignatureMethod
        val list: List<XMLStructure> = keyInfo.content ?: throw KeySelectorException("Null KeyInfo object!")
        list.filterIsInstance<X509Data>().forEach { x509data ->
            x509data.content.filterIsInstance<X509Certificate>().forEach {
                val publicKey = try {
                    it.publicKey
                } catch (ke: KeyException) {
                    throw KeySelectorException(ke)
                }
                if (algEquals(signatureMethod.algorithm, publicKey.algorithm)) {
                    return SimpleKeySelectorResult(publicKey)
                }
            }


        }
        throw KeySelectorException("No KeyValue element found!")
    }

    companion object {
        fun algEquals(algURI: String, algName: String): Boolean {
            return isValidDSA(algURI, algName) || isValidRSA(algURI, algName)
        }

        private fun isValidDSA(algURI: String, algName: String): Boolean {
            return "DSA".equals(algName, ignoreCase = true)
                    && (SignatureMethod.DSA_SHA1.equals(algURI, ignoreCase = true) || SignatureMethod.DSA_SHA256.equals(algURI, ignoreCase = true))
        }

        private fun isValidRSA(algURI: String, algName: String): Boolean {
            return "RSA".equals(algName, ignoreCase = true)
                    && (SignatureMethod.RSA_SHA1.equals(algURI, ignoreCase = true) || SignatureMethod.RSA_SHA256.equals(algURI, ignoreCase = true))
        }
    }
}

private class SimpleKeySelectorResult(private val publicKey: PublicKey) : KeySelectorResult {
    override fun getKey(): Key {
        return publicKey
    }
}