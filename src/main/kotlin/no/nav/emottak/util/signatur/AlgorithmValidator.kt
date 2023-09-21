package no.nav.emottak.util.signatur

import javax.xml.crypto.dsig.DigestMethod
import javax.xml.crypto.dsig.Reference
import javax.xml.crypto.dsig.SignatureMethod
import javax.xml.crypto.dsig.SignedInfo

private const val DIGEST_ALGORITHM_DEFAULT = "http://www.w3.org/2000/09/xmldsig#sha1"
private const val SIGNATURE_ALGORITHM_DEFAULT = "http://www.w3.org/2000/09/xmldsig#rsa-sha1"
private const val ALGORITHM_TYPE_SIGNATURE = "Signature"
private const val ALGORITHM_TYPE_DIGEST = "Message digest"

fun validateAlgorithms(
    signedInfo: SignedInfo,
    minimumSignatureAlgorithm: String = SIGNATURE_ALGORITHM_DEFAULT,
    minimumDigestAlgorithm: String = DIGEST_ALGORITHM_DEFAULT
) {
    val signatureAlgorithm = signedInfo.signatureMethod.algorithm
    validateMinimumSignature(signatureAlgorithm, minimumSignatureAlgorithm)
    for (ref in signedInfo.references as List<Reference>) {
        val digestAlgorithm = ref.digestMethod.algorithm
        validateMinimumDigest(digestAlgorithm, minimumDigestAlgorithm)
    }
}


private val signatureAlgorithmList =
    listOf(
        SignatureMethod.RSA_SHA1,
        SignatureMethod.RSA_SHA224,
        SignatureMethod.RSA_SHA256,
        SignatureMethod.RSA_SHA384,
        SignatureMethod.RSA_SHA512
    )

private val digestAlgorithmList =
    listOf(
        DigestMethod.SHA1,
        DigestMethod.SHA224,
        DigestMethod.SHA256,
        DigestMethod.SHA384,
        DigestMethod.SHA512
    )

private fun validateMinimumDigest(digestAlgorithm: String, minimumDigestAlgorithm: String): Boolean {
    return validateMinimum(digestAlgorithmList, digestAlgorithm, minimumDigestAlgorithm, ALGORITHM_TYPE_DIGEST)
}

private fun validateMinimumSignature(signatureAlgorithm: String, minimumSignatureAlgorithm: String): Boolean {
    return validateMinimum(signatureAlgorithmList, signatureAlgorithm, minimumSignatureAlgorithm, ALGORITHM_TYPE_SIGNATURE)
}

private fun validateMinimum(algorithmList: List<String>, algorithm: String, minimum: String, type: String): Boolean {
    val minIndex = algorithmList.indexOf(minimum)
    isMinimumAlgorithmSupported(minIndex, minimum, type)
    val index = algorithmList.indexOf(algorithm)
    isAlgorithmSupported(index, algorithm, type)
    isAlgorithmLessThanMinimum(index, minIndex, algorithm, minimum, type)
    return true
}

private fun isMinimumAlgorithmSupported(minIndex: Int, minimum: String, type: String) {
    if (minIndex < 0) {
        throw AlgorithmNotSupportedException("Unsupported specified minimum $type algorithm: $minimum")
    }
}

private fun isAlgorithmSupported(index: Int, algorithm: String, type: String) {
    if (index < 0) {
        throw AlgorithmNotSupportedException("Unsupported $type algorithm: $algorithm")
    }
}

private fun isAlgorithmLessThanMinimum(index: Int, minIndex: Int, algorithm: String, minimum: String, type: String) {
    if (index < minIndex) {
        throw AlgorithmNotSupportedException(
            "$type algorithm ($algorithm) is less than the specified minimum $type algorithm: $minimum"
        )
    }
}