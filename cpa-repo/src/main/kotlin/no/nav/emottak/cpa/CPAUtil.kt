package no.nav.emottak.cpa

import no.nav.emottak.cpa.feil.CpaValidationException
import no.nav.emottak.cpa.feil.SecurityException
import no.nav.emottak.message.ebxml.EbXMLConstants.EBMS_SERVICE_URI
import no.nav.emottak.message.ebxml.PartyTypeEnum
import no.nav.emottak.message.model.PartyId
import no.nav.emottak.message.model.SignatureDetails
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.Certificate
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.DeliveryChannel
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.DocExchange
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.EndpointTypeType
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PartyInfo
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.ProtocolType
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.Transport
import org.w3._2000._09.xmldsig_.X509DataType
import javax.xml.bind.JAXBElement

fun PartyInfo.getCertificateForEncryption(): ByteArray {
    // @TODO match role service action. ".first()" er ikke nok
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
    } else {
        val roles = this.collaborationRole.filter { it.role.name == role && it.serviceBinding.service.value == service }
        val canSend = roles.flatMap { it.serviceBinding.canSend.filter { cs -> cs.thisPartyActionBinding.action == action } }
        return canSend.firstOrNull()?.thisPartyActionBinding?.channelId?.first()?.value as DeliveryChannel? ?: throw CpaValidationException("Fant ikke SendDeliverChannel")
    }
}

fun PartyInfo.getReceiveDeliveryChannel(
    role: String,
    service: String,
    action: String
): DeliveryChannel {
    return if (EBMS_SERVICE_URI == service) {
        this.getDefaultDeliveryChannel(action)
    } else {
        val roles = this.collaborationRole.filter { it.role.name == role && it.serviceBinding.service.value == service }
        val canReceive = roles.flatMap { it.serviceBinding.canReceive.filter { cs -> cs.thisPartyActionBinding.action == action } }
            .also { if (it.size > 1) log.warn("Found more than 1 CanReceive (role='$role', service='$service', action='$action')") }
        val channel = canReceive.firstOrNull()?.thisPartyActionBinding?.channelId
            .also { if (it != null && it.size > 1) log.warn("Found more than 1 ChannelId (role='$role', service='$service', action='$action')") }
        return if (channel != null) channel.first().value as DeliveryChannel else this.getDefaultDeliveryChannel(action)
    }
}

private fun PartyInfo.getDefaultDeliveryChannel(
    action: String
): DeliveryChannel {
    return (this.overrideMshActionBinding.firstOrNull { it.action == action }?.channelId as DeliveryChannel?)
        ?: this.defaultMshChannelId as DeliveryChannel
}

fun PartyInfo.getReceiveEmailAddress(
    role: String,
    service: String,
    action: String
): String? {
    val deliveryChannel = this.getReceiveDeliveryChannel(role, service, action)
    return getReceiverEmailAddress(deliveryChannel)
}

fun PartyInfo.getSignalEmailAddress(
    action: String
): String? {
    val deliveryChannel = this.getDefaultDeliveryChannel(action)
    return getReceiverEmailAddress(deliveryChannel)
}

private fun getReceiverEmailAddress(deliveryChannel: DeliveryChannel): String? {
    val transport = deliveryChannel.getTransport() // SecurityException
    val transportReceiver = transport.transportReceiver
    if (transportReceiver == null || transportReceiver.transportProtocol.value != "SMTP") return null
    val endpoints = transportReceiver.endpoint
        .also { if (it.size > 1) log.warn("Found more than 1 email address (transportId='${transport.transportId}')") }
    val allPurpose = endpoints.filter { endpoint -> endpoint.type == EndpointTypeType.ALL_PURPOSE }
    return allPurpose.firstOrNull()?.uri ?: endpoints.firstOrNull()?.uri
        .also { if (endpoints.size > 0) log.warn("Did not find an allPurpose email address (transportId='${transport.transportId}') - using first() instead (${endpoints[0].type})") }
}

fun DeliveryChannel.getSigningCertificate(): Certificate {
    val docExchange = this.docExchangeId as DocExchange? ?: throw SecurityException("Fant ikke DocExchange")
    return if (
        docExchange.ebXMLSenderBinding != null &&
        docExchange.ebXMLSenderBinding?.senderNonRepudiation != null &&
        docExchange.ebXMLSenderBinding?.senderNonRepudiation?.signingCertificateRef != null
    ) {
        docExchange.ebXMLSenderBinding!!.senderNonRepudiation!!.signingCertificateRef.certId as Certificate
    } else {
        throw SecurityException("Finner ikke signeringssertifikat")
    }
}

fun DeliveryChannel.getTransport(): Transport = this.transportId as Transport? ?: throw SecurityException("Fant ikke transportkanal")

fun DeliveryChannel.getSenderTransportProtocolType(): ProtocolType {
    return this.getTransport().transportSender?.transportProtocol ?: throw SecurityException("Fant ikke transportkanal")
}

fun DeliveryChannel.getReceiverTransportProtocolType(): ProtocolType {
    return this.getTransport().transportReceiver?.transportProtocol ?: throw CpaValidationException("Fant ikke transportkanal")
}

fun DeliveryChannel.getSignatureAlgorithm(): String {
    val docExchange = this.docExchangeId as DocExchange? ?: throw SecurityException("Fant ikke DocExchange")
    if (docExchange.ebXMLSenderBinding != null &&
        docExchange.ebXMLSenderBinding?.senderNonRepudiation != null &&
        docExchange.ebXMLSenderBinding?.senderNonRepudiation?.signatureAlgorithm != null &&
        docExchange.ebXMLSenderBinding?.senderNonRepudiation?.signatureAlgorithm?.isNotEmpty() == true
    ) {
        val senderNonRepudiation = docExchange.ebXMLSenderBinding!!.senderNonRepudiation
        return if (senderNonRepudiation!!.signatureAlgorithm[0].w3C != null) {
            senderNonRepudiation.signatureAlgorithm[0].w3C!!
        } else {
            senderNonRepudiation.signatureAlgorithm[0].value!!
        }
    }
    throw SecurityException("Signature algorithm eksisterer ikke for DeliveryChannel")
}

fun DeliveryChannel.getHashFunction(): String {
    val docExchange = this.docExchangeId as DocExchange? ?: throw SecurityException("Fant ikke DocExchange")
    if (docExchange.ebXMLSenderBinding != null &&
        docExchange.ebXMLSenderBinding?.senderNonRepudiation != null &&
        docExchange.ebXMLSenderBinding?.senderNonRepudiation?.hashFunction != null
    ) {
        return docExchange.ebXMLSenderBinding!!.senderNonRepudiation!!.hashFunction
    }
    throw SecurityException("Hash Function eksisterer ikke for DeliveryChannel")
}

fun CollaborationProtocolAgreement.getPartnerPartyIdByType(partyType: PartyTypeEnum): org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PartyId? {
    return this.partyInfo.firstOrNull { partyInfo ->
        partyInfo.partyName != "NAV" // TODO Finne bedre måte å ekskludere NAV PartyInfo på
    }?.partyId?.firstOrNull { partyId ->
        partyId.type == partyType.type
    }
}

fun CollaborationProtocolAgreement.getPartyInfoByTypeAndID(partyType: String, partyId: String): PartyInfo {
    return this.partyInfo.firstOrNull { partyInfo ->
        partyInfo.partyId.any { party ->
            party.type == partyType && party.value == partyId
        }
    } ?: throw CpaValidationException("PartyID med type $partyType og id $partyId eksisterer ikke i CPA")
}

fun CollaborationProtocolAgreement.getPartyInfoByTypeAndID(partyId: List<PartyId>): PartyInfo {
    return this.partyInfo.firstOrNull { partyInfo ->
        partyInfo.partyId.any { party ->
            partyId.contains(PartyId(party.type!!, party.value!!)) // TODO O(n^2)...
        }
    } ?: throw CpaValidationException("Ingen match blant $partyId i CPA")
}

fun Certificate.getX509Certificate(): ByteArray {
    val jaxbElement = keyInfo.content?.first { it is JAXBElement<*> && it.value is X509DataType } as JAXBElement<*>
    val dataType = jaxbElement.value as X509DataType
    return (
        dataType.x509IssuerSerialOrX509SKIOrX509SubjectName?.first {
            it is JAXBElement<*> && it.name.localPart == "X509Certificate"
        } as JAXBElement<*>
        ).value as ByteArray
}
