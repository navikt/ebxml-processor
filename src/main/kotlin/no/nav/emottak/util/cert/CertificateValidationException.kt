package no.nav.emottak.util.cert

import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.ErrorCode
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType

class CertificateValidationException(message: String, exception: Exception? = null): EbmsException(message,ErrorCode.SECURITY_FAILURE, SeverityType.ERROR.value()!!, exception)