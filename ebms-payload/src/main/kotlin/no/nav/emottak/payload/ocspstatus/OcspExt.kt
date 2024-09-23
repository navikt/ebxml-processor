package no.nav.emottak.payload.ocspstatus

import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.ocsp.BasicOCSPResp
import java.io.IOException
import java.security.cert.X509Certificate

private val accessIdentifierOCSP = ASN1ObjectIdentifier("1.3.6.1.5.5.7.48.1")
internal fun X509Certificate.getOCSPUrlFromCertificate(): String {
    val url = this.getAuthorityInfoAccess(accessIdentifierOCSP)
    return url
}

private fun getStringFromGeneralName(names: ASN1Object): String {
    val taggedObject = names as DLTaggedObject
    return String(ASN1OctetString.getInstance(taggedObject, false).octets)
}

internal fun X509Certificate.getAuthorityInfoAccessObject(certificateChain: Array<X509CertificateHolder>): ASN1Object? {
    var authorityInfoAccess = this.getExtension(Extension.authorityInfoAccess.id)
    // val certificateChain = getCertificateChain(certificate.subjectX500Principal.name)
    var i = 0
    while (authorityInfoAccess == null && i < certificateChain.size) {
        authorityInfoAccess = (certificateChain[i].toASN1Structure() as X509Certificate).getExtension(
            Extension.authorityInfoAccess.id
        )
        i++
    }
    return authorityInfoAccess
}

internal fun X509Certificate.getOCSPUrl(): String {
    val x500Name = X500Name(this.issuerX500Principal.name)
    return certificateAuthorities.caList.firstOrNull {
        it.x500Name == x500Name
    }?.ocspUrl ?: this.getOCSPUrlFromCertificate()
}

fun X509Certificate.getAuthorityInfoAccess(method: ASN1ObjectIdentifier): String {
    val obj = this.getExtension(Extension.authorityInfoAccess.id) ?: return ""
    val accessDescriptions = obj as ASN1Sequence // ASN1Sequence.getInstance(obj)
    accessDescriptions.forEach {
        val accessDescription = it as ASN1Sequence
        if (accessDescription.size() == 2) {
            val identifier = accessDescription.getObjectAt(0) as ASN1ObjectIdentifier
            if (method.equals(identifier)) {
                return getStringFromGeneralName(accessDescription.getObjectAt(1) as ASN1Object)
            }
        }
    }
    return ""
}

internal fun ExtensionsGenerator.addServiceLocator(
    certificate: X509Certificate,
    provider: X500Name,
    certificateChain: Array<X509CertificateHolder>
) {
    certificate.getAuthorityInfoAccessObject(certificateChain)?.let {
        val vector = ASN1EncodableVector()
        vector.add(it.toASN1Primitive())
        vector.add(provider)
        this.addExtension(
            OCSPObjectIdentifiers.id_pkix_ocsp_service_locator,
            false,
            DEROctetString(DERSequence(vector))
        )
    }
}

internal fun ExtensionsGenerator.addSsnExtension() {
    this.addExtension(ssnPolicyID, false, DEROctetString(byteArrayOf(0)))
}

internal fun getSSN(bresp: BasicOCSPResp): String {
    val response = bresp.responses[0]
    var ssn = getSsn(response.getExtension(ssnPolicyID))
    if ("" == ssn) {
        ssn = getSsn(bresp.getExtension(ssnPolicyID))
    }
    return ssn
}

private fun getSsn(ssnExtension: Extension?): String {
    return if (ssnExtension != null) {
        try {
            String(ssnExtension.extnValue.encoded).replace(Regex("\\D"), "")
        } catch (e: IOException) {
            throw SertifikatError("Failed to extract SSN", cause = e)
        }
    } else {
        ""
    }
}
