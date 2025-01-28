package no.nav.emottak.payload.ocspstatus

import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import java.security.cert.X509Certificate
import java.util.regex.Pattern
import javax.naming.InvalidNameException
import javax.naming.ldap.LdapName
import javax.naming.ldap.Rdn
import javax.security.auth.x500.X500Principal

private val policyIdVirksomhet = listOf(
    "[2.16.578.1.26.1.0.9.9]", // Nav Test CA
    "[2.16.578.1.26.1.0.3.2]", // buypass.test.policy.id.agency
    "[2.16.578.1.26.1.3.2]", // buypass.prod.policy.id.agency
    "[2.16.578.1.29.13.1.1.0]", // commfides.prod.policy.id.agency
    "[2.16.578.1.29.913.1.1.0]", // commfides.test.policy.id.agency
    "[2.16.578.1.29.13.200.1.0]" // commfides.g3.prod.policy.id.agency
)

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

fun X509Certificate.getExtension(oid: String): ASN1Primitive? {
    val value = this.getExtensionValue(oid)
    return if (value == null) {
        null
    } else {
        val ap = JcaX509ExtensionUtils.parseExtensionValue(value)
        ASN1Sequence.getInstance(ap.encoded)
    }
}

internal fun X509Certificate.getOrganizationNumber(): String? {
    return if (this.isVirksomhetssertifikat()) {
        getOrganizationNumberFromDN(this.subjectX500Principal.getName(X500Principal.RFC1779))
    } else {
        null
    }
}

internal fun X509Certificate.isVirksomhetssertifikat(): Boolean {
    val bytes = this.getExtensionValue(Extension.certificatePolicies.id)
    val octetString = ASN1Primitive.fromByteArray(bytes) as ASN1OctetString
    val policies = ASN1Primitive.fromByteArray(octetString.octets) as DLSequence
    return policies.map { policy ->
        (policy as DLSequence).toString()
    }.any { it in policyIdVirksomhet }
}

private fun newLdapName(name: String): LdapName {
    return try {
        LdapName(name)
    } catch (e: InvalidNameException) {
        throw SertifikatError("failed to create LdapName", cause = e)
    }
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
