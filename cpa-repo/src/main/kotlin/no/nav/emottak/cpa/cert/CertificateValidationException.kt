package no.nav.emottak.cpa.cert

import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.ErrorCode
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType

class CertificateValidationException(message: String, exception: Exception? = null) : EbmsException(message, ErrorCode.SECURITY_FAILURE, SeverityType.ERROR.value()!!, exception)
