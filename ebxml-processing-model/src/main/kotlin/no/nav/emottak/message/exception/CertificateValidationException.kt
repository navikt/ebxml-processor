package no.nav.emottak.message.exception

import no.nav.emottak.message.model.ErrorCode
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType
import java.security.cert.X509Certificate

class CertificateValidationException(
    message: String,
    exception: Exception? = null,
    recoverable: Boolean = true,
    val x509Certificate: X509Certificate? = null
) : EbmsException(
    message = message,
    errorCode = ErrorCode.SECURITY_FAILURE,
    severity = SeverityType.ERROR.value()!!,
    recoverable = recoverable,
    cause = exception
)
