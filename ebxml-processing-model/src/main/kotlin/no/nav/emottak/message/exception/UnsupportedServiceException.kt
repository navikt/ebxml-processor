package no.nav.emottak.message.exception

import no.nav.emottak.message.model.ErrorCode
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType

// Tjenester som ikke (lenger) støttes
val UNSUPPORTED_SERVICES = mapOf(
    "PasientlisteForesporsel" to "Tjeneste PasientlisteForesporsel utfaset siden 1. april 2026. Kontakt HDIR for mer informasjon",
    "S-LOM".unsupportedMessage("BehandleKrav"),
    "S-EgenandelForesporsel".unsupportedMessage("HarBorgerFrikortMengde"),
    "S-SYKEMELD".unsupportedMessage("Sykmelding"),
    "S-DIALOG_FORESPORSEL".unsupportedMessage(),
    "S-DIALOG_NOTAT".unsupportedMessage(),
    "S-Svarmelding".unsupportedMessage()
)

private fun String.unsupportedMessage(suggestedService: String? = null): Pair<String, String> {
    val msg = "Tjeneste '%s' støttes ikke i Nav.".format(this)
    val message = if (suggestedService == null) msg else "%s Bruk i stedet '%s'.".format(msg, suggestedService)
    return this to message
}

class UnsupportedServiceException(override val message: String) :
    EbmsException(
        message = message,
        errorCode = ErrorCode.NOT_SUPPORTED,
        severity = SeverityType.ERROR.value()!!,
        recoverable = false
    )
