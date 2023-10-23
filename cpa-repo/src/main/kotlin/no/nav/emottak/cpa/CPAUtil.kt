package no.nav.emottak.cpa

import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import no.nav.emottak.EBMS_SERVICE_URI
import no.nav.emottak.melding.model.SignatureDetails
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.Certificate
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.DeliveryChannel
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.DocExchange
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PartyInfo
import org.w3._2000._09.xmldsig_.X509DataType
import javax.xml.bind.JAXBElement

private val cpaUtil = CPAUtil()

fun getCpa(id: String) = cpaUtil.getCpa(id)


private class CPAUtil {

    fun getCpa(id: String): CollaborationProtocolAgreement? {
        //TODO
        val testCpaString = String(this::class.java.classLoader.getResource("cpa/nav-qass-35065.xml").readBytes())
        return unmarshal(testCpaString, CollaborationProtocolAgreement::class.java)
    }

}

fun PartyInfo.getCertificateForEncryption(): ByteArray {
    val encryptionCert = this.collaborationRole.first().applicationCertificateRef.first().certId as Certificate
    return encryptionCert.getX509Certificate()
}

fun PartyInfo.getCertificateForSignatureValidation(
    role: String,
    service: String,
    action: String
): SignatureDetails {
    val deliveryChannel = this.getSendDeliveryChannel(role, service, action)
    return SignatureDetails(
        certificate = deliveryChannel.getSigningCertificate().getX509Certificate(),
        signatureAlgorithm = deliveryChannel.getSignatureAlgorithm(),
        hashFunction = deliveryChannel.getHashFunction()
    )
}

fun PartyInfo.getSendDeliveryChannel(
    role: String,
    service: String,
    action: String
): DeliveryChannel {
    return if (EBMS_SERVICE_URI == service) {
        this.getDefaultDeliveryChannel(action)
    }
    else {
        val roles = this.collaborationRole.filter{ it.role.name == role && it.serviceBinding.service.value == service}
        val canSend = roles.flatMap { it.serviceBinding.canSend.filter { cs -> cs.thisPartyActionBinding.action == action } }
        return canSend.firstOrNull()?.thisPartyActionBinding?.channelId?.first()?.value as DeliveryChannel? ?: throw BadRequestException("Fant ikke SendDeliverChannel")
    }
}

private fun PartyInfo.getDefaultDeliveryChannel(
    action: String
): DeliveryChannel {
    return (this.overrideMshActionBinding.firstOrNull{ it.action == action }?.channelId as DeliveryChannel?)
        ?: this.defaultMshChannelId as DeliveryChannel
}


fun DeliveryChannel.getSigningCertificate(): Certificate {
    val docExchange = this.docExchangeId as DocExchange? ?: throw BadRequestException("Fant ikke DocExchange")
    return if (
        docExchange.ebXMLSenderBinding != null &&
        docExchange.ebXMLSenderBinding?.senderNonRepudiation != null &&
        docExchange.ebXMLSenderBinding?.senderNonRepudiation?.signingCertificateRef != null
    ) docExchange.ebXMLSenderBinding!!.senderNonRepudiation!!.signingCertificateRef.certId as Certificate else throw RuntimeException("Finner ikke signeringssertifikat")
}

fun DeliveryChannel.getSignatureAlgorithm(): String
{
    val docExchange = this.docExchangeId as DocExchange? ?: throw BadRequestException("Fant ikke DocExchange")
    if (docExchange.ebXMLSenderBinding != null
        && docExchange.ebXMLSenderBinding?.senderNonRepudiation != null
        && docExchange.ebXMLSenderBinding?.senderNonRepudiation?.signatureAlgorithm != null
        && docExchange.ebXMLSenderBinding?.senderNonRepudiation?.signatureAlgorithm?.isNotEmpty() == true
    )
    {
        val senderNonRepudiation = docExchange.ebXMLSenderBinding!!.senderNonRepudiation;
        return if (senderNonRepudiation!!.signatureAlgorithm[0].w3C != null)
            senderNonRepudiation.signatureAlgorithm[0].w3C!!
        else senderNonRepudiation.signatureAlgorithm[0].value!!
    }
    throw BadRequestException("Signature algorithm eksisterer ikke for DeliveryChannel")
}

fun DeliveryChannel.getHashFunction(): String
{
    val docExchange = this.docExchangeId as DocExchange? ?: throw BadRequestException("Fant ikke DocExchange")
    if (docExchange.ebXMLSenderBinding != null
        && docExchange.ebXMLSenderBinding?.senderNonRepudiation != null
        && docExchange.ebXMLSenderBinding?.senderNonRepudiation?.hashFunction != null)
        return docExchange.ebXMLSenderBinding!!.senderNonRepudiation!!.hashFunction
    throw BadRequestException("Hash Function eksisterer ikke for DeliveryChannel")
}

fun CollaborationProtocolAgreement.getPartyInfoByTypeAndID(partyType: String, partyId: String): PartyInfo
{
    return this.partyInfo.firstOrNull { partyInfo ->
        partyInfo.partyId.any { party ->
            party.type == partyType && party.value == partyId
        }
    } ?: throw NotFoundException("PartyID med type $partyType og id $partyId eksisterer ikke i CPA")
}

fun Certificate.getX509Certificate(): ByteArray {
    val jaxbElement = keyInfo.content?.first { it is JAXBElement<*> && it.value is X509DataType } as JAXBElement<*>
    val dataType = jaxbElement.value as X509DataType
    return (dataType.x509IssuerSerialOrX509SKIOrX509SubjectName?.first {
            it is JAXBElement<*> && it.name.localPart == "X509Certificate"
        } as JAXBElement<*>).value as ByteArray
}