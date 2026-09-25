package no.nav.emottak.message.exception

import no.nav.emottak.message.model.ErrorCode
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType

// Tjenester som ikke (lenger) støttes
val UNSUPPORTED_SERVICE_PASIENTLISTEFORESPORSEL = "PasientlisteForesporsel" to "Tjeneste PasientlisteForesporsel utfaset siden 1. april 2026. Kontakt HDIR for mer informasjon"

class UnsupportedServiceException(override val message: String) :
    EbmsException(
        message = message,
        errorCode = ErrorCode.DELIVERY_FAILURE,
        severity = SeverityType.ERROR.value()!!,
        recoverable = false
    )
