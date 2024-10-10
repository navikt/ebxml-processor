package no.nav.emottak.cpa.validation

import no.nav.emottak.constants.EbXMLConstants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.constants.EbXMLConstants.EBMS_SERVICE_URI
import no.nav.emottak.constants.EbXMLConstants.MESSAGE_ERROR_ACTION
import no.nav.emottak.cpa.feil.CpaValidationException
import no.nav.emottak.cpa.getPartyInfoByTypeAndID
import no.nav.emottak.message.model.Addressing
import no.nav.emottak.message.model.ValidationRequest
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PartyInfo
import java.time.Instant
import java.util.Date

fun CollaborationProtocolAgreement.validate(validationRequest: ValidationRequest) {
    validateCpaId(validationRequest.cpaId)
    validateCpaDatoGyldig()
    hasRoleServiceActionCombo(validationRequest.addressing)
}

@Throws(CpaValidationException::class)
fun CollaborationProtocolAgreement.hasRoleServiceActionCombo(addressing: Addressing) {
    if (addressing.service == EBMS_SERVICE_URI) {
        if (addressing.action != ACKNOWLEDGMENT_ACTION && addressing.action != MESSAGE_ERROR_ACTION) {
            throw CpaValidationException("Service $EBMS_SERVICE_URI støtter ikke action ${addressing.action}")
        }
        return
    }
    val fromParty = this.getPartyInfoByTypeAndID(addressing.from.partyId)
    val fromRole = addressing.from.role

    val toParty = this.getPartyInfoByTypeAndID(addressing.to.partyId)
    val toRole = addressing.to.role

    partyInfoHasRoleServiceActionCombo(fromParty, fromRole, addressing.service, addressing.action, MessageDirection.SEND)
    partyInfoHasRoleServiceActionCombo(toParty, toRole, addressing.service, addressing.action, MessageDirection.RECEIVE)
}

@Throws(CpaValidationException::class)
fun partyInfoHasRoleServiceActionCombo(partyInfo: PartyInfo, role: String, service: String, action: String, direction: MessageDirection) {
    val partyWithRole = partyInfo.collaborationRole.firstOrNull { r ->
        r.role.name == role && r.serviceBinding.service.value == service
    } ?: throw CpaValidationException("Role $role matcher ikke service $service for party ${partyInfo.partyName}")
    when (direction) {
        MessageDirection.SEND -> partyWithRole.serviceBinding.canSend.firstOrNull { a -> a.thisPartyActionBinding.action == action }
            ?: throw CpaValidationException("Action $action matcher ikke service $service for sending party ${partyInfo.partyName}")
        MessageDirection.RECEIVE -> partyWithRole.serviceBinding.canReceive.firstOrNull { a -> a.thisPartyActionBinding.action == action }
            ?: throw CpaValidationException("Action $action matcher ikke service $service for receiving party ${partyInfo.partyName}")
    }
}

fun CollaborationProtocolAgreement.validateCpaId(cpaId: String) {
    if (this.cpaid != cpaId) {
        throw CpaValidationException("Funnet CPA (ID: ${this.cpaid}) matcher ikke cpaid til melding: $cpaId")
    }
}

fun CollaborationProtocolAgreement.validateCpaDatoGyldig() {
    /* Fixme:
        Den genererte kontrakten tolker feltet som java.Date. Dette kan i realiteten være LocalDateTime
        Eksempel: <tp:Start>2009-11-26T14:26:21Z</tp:Start>
     */
    if (!Date.from(Instant.now()) // TODO: Mottakstidspunkt?
        .let { it.after(this.start) && it.before(this.end) }
    ) {
        throw CpaValidationException("CPA er ikke gyldig på meldingstidspunkt. Start: ${this.start} - End: ${this.end}")
    }
}

enum class MessageDirection { SEND, RECEIVE }
