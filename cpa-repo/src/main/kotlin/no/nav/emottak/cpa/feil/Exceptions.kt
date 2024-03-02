package no.nav.emottak.cpa.feil

import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.ErrorCode
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType

open class CpaValidationException(
    message: String,
    errorCode: ErrorCode = ErrorCode.DELIVERY_FAILURE,
    severity: String = SeverityType.ERROR.value(),
    exception: Exception? = null
) : EbmsException(message, errorCode, severity, exception)

open class SecurityException(
    message: String,
    errorCode: ErrorCode = ErrorCode.SECURITY_FAILURE,
    severity: String = SeverityType.ERROR.value(),
    exception: java.lang.Exception? = null
) : EbmsException(message, errorCode, severity, exception)

open class PartnerNotFoundException(
    message: String,
    exception: java.lang.Exception? = null
) : Exception(message, exception)

open class MultiplePartnerException(
    message: String,
    exception: java.lang.Exception? = null
) : Exception(message, exception)
