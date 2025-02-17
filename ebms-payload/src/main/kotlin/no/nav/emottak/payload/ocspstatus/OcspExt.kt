package no.nav.emottak.payload.ocspstatus

import no.nav.emottak.payload.log
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.ocsp.BasicOCSPResp
import java.io.IOException
import java.math.BigInteger
import java.security.cert.X509Certificate

internal fun X509Certificate.getAuthorityInfoAccessObject(certificateChain: Array<X509CertificateHolder>): ASN1Object? {
    var authorityInfoAccess = this.getExtension(Extension.authorityInfoAccess.id)
    var i = 0
    while (authorityInfoAccess == null && i < certificateChain.size) {
        authorityInfoAccess = (certificateChain[i].toASN1Structure() as X509Certificate).getExtension(
            Extension.authorityInfoAccess.id
        )
        i++
    }
    return authorityInfoAccess
}

internal fun ExtensionsGenerator.addServiceLocator(
    certificate: X509Certificate,
    provider: X500Name,
    certificateChain: Array<X509CertificateHolder>
) {
    certificate.getAuthorityInfoAccessObject(certificateChain)?.let {
        val vector = ASN1EncodableVector().apply {
            add(it.toASN1Primitive())
            add(provider)
        }
        this.addExtension(
            OCSPObjectIdentifiers.id_pkix_ocsp_service_locator,
            false,
            DEROctetString(DERSequence(vector))
        )
    }
}

internal fun ExtensionsGenerator.addNonceExtension() {
    this.addExtension(
        OCSPObjectIdentifiers.id_pkix_ocsp_nonce,
        false,
        DEROctetString(BigInteger.valueOf(System.currentTimeMillis()).toByteArray())
    )
}

val ssnPolicyID = ASN1ObjectIdentifier("2.16.578.1.16.3.2")
internal fun ExtensionsGenerator.addSsnExtension() = this.addExtension(ssnPolicyID, false, DEROctetString(byteArrayOf(0)))

internal fun getSSN(bresp: BasicOCSPResp): String {
    val response = bresp.responses[0]
    var ssn = getSSN(response.getExtension(ssnPolicyID))
    if ("" == ssn) {
        ssn = getSSN(bresp.getExtension(ssnPolicyID))
    }
    return ssn
}

private fun getSSN(ssnExtension: Extension?): String {
    ssnExtension ?: return "".also { log.warn("OCSP FNR-extension is null") }
    try {
        return String(ssnExtension.extnValue.encoded).replace(Regex("\\D"), "")
    } catch (e: IOException) {
        throw SertifikatError("Failed to extract SSN", cause = e)
    }
}
