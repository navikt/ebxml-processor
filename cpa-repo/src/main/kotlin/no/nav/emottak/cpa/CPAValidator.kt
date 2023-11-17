package no.nav.emottak.cpa

import no.nav.emottak.constants.EbXMLConstants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.constants.EbXMLConstants.EBMS_SERVICE_URI
import no.nav.emottak.constants.EbXMLConstants.MESSAGE_ERROR_ACTION
import no.nav.emottak.cpa.feil.CpaValidationException
import no.nav.emottak.melding.model.Header
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PartyInfo
import java.time.Instant
import java.util.Date


fun CollaborationProtocolAgreement.validate(header: Header) {
    validateCpaId(header)
    validateCpaDatoGyldig(header)
    hasRoleServiceActionCombo(header)
}

@Throws(CpaValidationException::class)
fun CollaborationProtocolAgreement.hasRoleServiceActionCombo(header: Header) {
    if(header.service == EBMS_SERVICE_URI) {
        if (header.action != ACKNOWLEDGMENT_ACTION && header.action != MESSAGE_ERROR_ACTION)
            throw CpaValidationException("Service $EBMS_SERVICE_URI støtter ikke action ${header.action}")
        return
    }
    val fromParty = this.getPartyInfoByTypeAndID(header.from.partyId)
    val fromRole = header.from.role

    val toParty = this.getPartyInfoByTypeAndID(header.to.partyId)
    val toRole = header.to.role

    partyInfoHasRoleServiceActionCombo(fromParty, fromRole, header.service, header.action, MessageDirection.SEND)
    partyInfoHasRoleServiceActionCombo(toParty, toRole, header.service, header.action, MessageDirection.RECEIVE)
}

@Throws(CpaValidationException::class)
fun partyInfoHasRoleServiceActionCombo(partyInfo: PartyInfo, role: String, service: String, action: String, direction: MessageDirection) {
    val partyWithRole = partyInfo.collaborationRole.firstOrNull { r -> r.role.name == role }
        ?: throw CpaValidationException("Role $role matcher ikke party")
    if (partyWithRole.serviceBinding.service.value != service)
        throw CpaValidationException("Service $service matcher ikke role $role for party")
    when (direction) {
        MessageDirection.SEND -> partyWithRole.serviceBinding.canSend.firstOrNull { a -> a.thisPartyActionBinding.action == action }
            ?: throw CpaValidationException("Action $action matcher ikke service $service")
        MessageDirection.RECEIVE -> partyWithRole.serviceBinding.canReceive.firstOrNull { a -> a.thisPartyActionBinding.action == action }
            ?: throw CpaValidationException("Action $action matcher ikke service $service")
    }
}

fun CollaborationProtocolAgreement.validateCpaId(header: Header) {
    if(this.cpaid != header.cpaId)
        throw CpaValidationException("Funnet CPA (ID: ${this.cpaid}) matcher ikke cpaid til melding: ${header.cpaId}")
}

fun CollaborationProtocolAgreement.validateCpaDatoGyldig(header: Header) {
    /* Fixme:
        Den genererte kontrakten tolker feltet som java.Date. Dette kan i realiteten være LocalDateTime
        Eksempel: <tp:Start>2009-11-26T14:26:21Z</tp:Start>
     */
    if(!Date.from(Instant.now()) // TODO: Mottakstidspunkt?
        .let { it.after(this.start) && it.before(this.end) })
        throw CpaValidationException("Cpa ID: [${header.cpaId}] CPA er ikke gyldig på meldingstidspunkt.")
}

enum class MessageDirection { SEND, RECEIVE }