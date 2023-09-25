package no.nav.emottak.cpa

import no.nav.emottak.melding.model.Header
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement


fun CollaborationProtocolAgreement.validate(header: Header) {

}

class ValidationException(reason: String) : Exception(reason)