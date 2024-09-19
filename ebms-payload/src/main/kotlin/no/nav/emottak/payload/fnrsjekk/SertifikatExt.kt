package no.nav.emottak.payload.fnrsjekk

import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.util.regex.Pattern
import javax.naming.InvalidNameException
import javax.naming.ldap.LdapName
import javax.naming.ldap.Rdn
import javax.security.auth.x500.X500Principal


private val policyIdVirksomhet = listOf(
    "[2.16.578.1.26.1.0.3.2]", //buypass.test.policy.id.agency
    "[2.16.578.1.26.1.3.2]", //buypass.prod.policy.id.agency
    "[2.16.578.1.29.13.1.1.0]", //commfides.prod.policy.id.agency
    "[2.16.578.1.29.913.1.1.0]", //commfides.test.policy.id.agency
    "[2.16.578.1.29.13.200.1.0]" //commfides.g3.prod.policy.id.agency
)

private val accessIdentifierOCSP = ASN1ObjectIdentifier("1.3.6.1.5.5.7.48.1")



private val EXTRACT_ORGNO_PATTERN = Pattern.compile("^(\\d{9})$|^.*-\\s*(\\d{9})$")

private val DN_TYPES_IN_SEARCHORDER = arrayOf("ou", "serialnumber", "OID.2.5.4.97", "OID.2.5.4.5", "o")
internal fun X509Certificate.getSEIDVersion(): SEIDVersion {
    val seid2organizationIdentifierOID = "OID.2.5.4.97"

    val issuer = newLdapName(this.issuerX500Principal.getName(X500Principal.RFC1779))

    issuer.rdns.firstOrNull { rdn -> rdn.type.equals(seid2organizationIdentifierOID) }?.let {
        if (it.value.toString().startsWith("NTRNO", ignoreCase = true)) {
            return SEIDVersion.SEID20
        }
    }
    return SEIDVersion.SEID10
}

private fun newLdapName(name: String): LdapName {
    return try {
        LdapName(name)
    } catch (e: InvalidNameException) {
        throw SertifikatError("failed to create LdapName", cause = e)
    }
}

internal fun X509Certificate.getOrganizationNumber(): String? {
    return if (this.isVirksomhetssertifikat())
        getOrganizationNumberFromDN(this.subjectX500Principal.getName(X500Principal.RFC1779))
    else
        null
}

private fun getOrganizationNumberFromDN(dn: String): String {
    try {
        val name: LdapName = newLdapName(dn)
        DN_TYPES_IN_SEARCHORDER.forEach { type ->
            val number = getOrganizationNumberFromRDN(name.rdns, type)
            if (number != null) return number
        }
    } catch (e: Exception) {
        return ""
    }
    return ""
}

private fun getOrganizationNumberFromRDN(rdns: List<Rdn>, type: String): String? {
    rdns.forEach { rdn ->
        if (type.equals(rdn.type, ignoreCase = true)) {
            val matcher = EXTRACT_ORGNO_PATTERN.matcher(rdn.value as String)
            if (matcher.matches()) {
                return if (matcher.group(2) != null) matcher.group(2) else matcher.group(1)
            }
        }
    }
    return null
}

internal fun X509Certificate.isVirksomhetssertifikat(): Boolean {
    val bytes = this.getExtensionValue(Extension.certificatePolicies.id)
    val octetString = ASN1Primitive.fromByteArray(bytes) as ASN1OctetString
    val policies = ASN1Primitive.fromByteArray(octetString.octets) as DLSequence
    return policies.map { policy ->
        (policy as DLSequence).toString()
    }.any {  it in policyIdVirksomhet }
}

internal fun X509Certificate.getAuthorityInfoAccessObject(certificateChain: Array<X509CertificateHolder>): ASN1Object? {
    var aia = this.getExtension(Extension.authorityInfoAccess.id)
    //val certificateChain = getCertificateChain(certificate.subjectX500Principal.name)
    var i = 0
    while (aia == null && i < certificateChain.size) {
        aia = (certificateChain[i].toASN1Structure() as X509Certificate).getExtension(
            Extension.authorityInfoAccess.id
        )
        i++
    }
    return aia
}




fun X509Certificate.getExtension(oid: String): ASN1Primitive? {
    val value = this.getExtensionValue(oid)
    return if (value == null) {
        null
    } else {
        val ap = JcaX509ExtensionUtils.parseExtensionValue(value)
        ASN1Sequence.getInstance(ap.encoded)
    }
}

internal fun addNonceExtension(extensionsGenerator: ExtensionsGenerator) {
    val nonce = BigInteger.valueOf(System.currentTimeMillis())
    extensionsGenerator.addExtension(
        OCSPObjectIdentifiers.id_pkix_ocsp_nonce,
        false,
        DEROctetString(nonce.toByteArray())
    )
}

internal fun addSsnExtension(extensionsGenerator: ExtensionsGenerator) {
    extensionsGenerator.addExtension(ssnPolicyID, false, DEROctetString(byteArrayOf(0)))
}


internal fun X509Certificate.addServiceLocator(
    extensionsGenerator: ExtensionsGenerator,
    provider: X500Name,
    certificateChain: Array<X509CertificateHolder>
) {
    this.getAuthorityInfoAccessObject(certificateChain)?.let {
        val vector = ASN1EncodableVector()
        vector.add(it.toASN1Primitive())
        vector.add(provider)
        extensionsGenerator.addExtension(
            OCSPObjectIdentifiers.id_pkix_ocsp_service_locator,
            false,
            DEROctetString(DERSequence(vector))
        )
    }
}

internal fun X509Certificate.getOCSPUrlFromCertificate(): String {
    val url = this.getAuthorityInfoAccess( accessIdentifierOCSP)
    return url
}

fun X509Certificate.getAuthorityInfoAccess(method: ASN1ObjectIdentifier): String {
    val obj = this.getExtension(Extension.authorityInfoAccess.id) ?: return ""
    val accessDescriptions = obj as ASN1Sequence //ASN1Sequence.getInstance(obj)
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

private fun getStringFromGeneralName(names: ASN1Object): String {
    val taggedObject = names as DLTaggedObject
    return String(ASN1OctetString.getInstance(taggedObject, false).octets)
}

private fun X509Certificate.getCertificatePolicies(): List<String> {
    val bytes = this.getExtensionValue(Extension.certificatePolicies.id)
    val octetString = ASN1Primitive.fromByteArray(bytes) as ASN1OctetString
    val policies = ASN1Primitive.fromByteArray(octetString.octets) as DLSequence
    return policies.map { policy ->
        (policy as DLSequence).toString()
    }.toList()
}


