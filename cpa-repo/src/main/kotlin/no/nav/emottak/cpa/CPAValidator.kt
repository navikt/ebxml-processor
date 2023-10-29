package no.nav.emottak.cpa

import no.nav.emottak.melding.model.Header
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import java.time.Instant
import java.util.Date


fun CollaborationProtocolAgreement.validate(header: Header) {
    validateCpaId(header)
    validateCpaDatoGyldig(header)
    validateRole(header)
    validateService(header)
    validateAction(header)
}

fun CollaborationProtocolAgreement.validateRole(header: Header) {
    if(!this.partyInfo.any{ p -> p.collaborationRole.any { c -> c.role.name == header.from.role }})
        throw CpaValidationException("Role matcher ikke CPA")
}

fun CollaborationProtocolAgreement.validateCpaId(header: Header) {
    if(this.cpaid != header.cpaId)
        throw CpaValidationException("Funnet CPA (ID: ${this.cpaid}) matcher ikke cpaid til melding: ${header.cpaId}")
}
fun CollaborationProtocolAgreement.validateAction(header: Header) {
    if(!this.partyInfo
            .any{ p -> p.collaborationRole
                .any { c -> c.serviceBinding.canSend
                    .any{ action -> action.thisPartyActionBinding.action == header.action }}})
        throw CpaValidationException("Action matcher ikke CPA")
}

fun CollaborationProtocolAgreement.validateService(header: Header) {
    if(!this.partyInfo.any{p -> p.collaborationRole.any{c -> header.service == c.serviceBinding.service.value }})
        throw CpaValidationException("Service matcher ikke CPA")
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

open class CpaValidationException(
    message: String): Exception(message)

