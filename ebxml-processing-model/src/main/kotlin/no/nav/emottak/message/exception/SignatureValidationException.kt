package no.nav.emottak.message.exception

import no.nav.emottak.message.model.ErrorCode
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType

class SignatureValidationException(message: String, exception: Exception? = null, recoverable: Boolean = true) : EbmsException(
    message = message,
    errorCode = ErrorCode.SECURITY_FAILURE,
    severity = SeverityType.ERROR.value()!!,
    recoverable = recoverable,
    cause = exception
)
