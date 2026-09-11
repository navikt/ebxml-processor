package no.nav.emottak.validering.sertifikat

import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.ErrorCode
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType

class CertificateValidationException(message: String, exception: Exception? = null) : EbmsException(
    message = message,
    errorCode = ErrorCode.SECURITY_FAILURE,
    severity = SeverityType.ERROR.value()!!,
    exception = exception
)
