package no.nav.emottak.cpa

import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.Certificate
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.w3._2000._09.xmldsig_.X509DataType
import javax.xml.bind.JAXBElement

private val cpaUtil = CPAUtil()

fun getCpa(id: String) = cpaUtil.getCpa(id)


private class CPAUtil {

    fun getCpa(id: String): CollaborationProtocolAgreement? {
        //TODO
        val testCpaString = String(this::class.java.classLoader.getResource("nav-qass-35065.xml").readBytes())
        return unmarshal(testCpaString, CollaborationProtocolAgreement::class.java)
    }

}

fun CollaborationProtocolAgreement.getCertificateForEncryption(herId: String): ByteArray {
    val partyInfo = this.partyInfo.first { partyInfo ->
        partyInfo.partyId.any { partyId ->
            partyId.type == "HER" && partyId.value == herId
        }
    }
    val encryptionCert = partyInfo.collaborationRole.first().applicationCertificateRef.first().certId as Certificate
    val datatype =
        encryptionCert.keyInfo.content?.filterIsInstance(JAXBElement::class.java)?.firstOrNull()?.value as X509DataType
    return datatype.x509IssuerSerialOrX509SKIOrX509SubjectName?.filterIsInstance(JAXBElement::class.java)
        ?.firstOrNull()?.value as ByteArray
}