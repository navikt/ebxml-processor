package no.nav.emottak.payload.error

import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.util.signatur.SignatureException
import no.nav.emottak.utils.kafka.model.EventType
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType

open class PayloadException(message: String?, cause: Throwable?) : Exception(message, cause)

open class CertificateException(message: String, cause: Exception? = null) : PayloadException(message, cause)
class OCSPValidationFnrBlankError(message: String, cause: Exception? = null) : CertificateException(message, cause)
class CompressionException(message: String, cause: Exception? = null) : PayloadException(message, cause)
class DecompressionException(message: String, cause: Exception? = null) : PayloadException(message, cause)
class DecryptionException(message: String, cause: Exception? = null) : PayloadException(message, cause)
class EncryptionException(message: String, cause: Exception? = null) : PayloadException(message, cause)
class JuridiskLoggException(message: String, cause: Exception? = null) : PayloadException(message, cause)

fun Throwable.convertToFeil(): Feil = when (this) {
    is JuridiskLoggException -> Feil(ErrorCode.DELIVERY_FAILURE, localizedMessage, SeverityType.ERROR.value())
    is EncryptionException -> Feil(ErrorCode.SECURITY_FAILURE, localizedMessage, SeverityType.ERROR.value())
    is DecryptionException -> Feil(ErrorCode.SECURITY_FAILURE, localizedMessage, SeverityType.ERROR.value())
    is CompressionException -> Feil(ErrorCode.SECURITY_FAILURE, localizedMessage, SeverityType.ERROR.value())
    is DecompressionException -> Feil(ErrorCode.SECURITY_FAILURE, localizedMessage, SeverityType.ERROR.value())
    is SignatureException -> Feil(ErrorCode.SECURITY_FAILURE, localizedMessage, SeverityType.ERROR.value())
    is CertificateException -> Feil(ErrorCode.SECURITY_FAILURE, localizedMessage, SeverityType.ERROR.value())
    else -> Feil(ErrorCode.UNKNOWN, this.localizedMessage, SeverityType.ERROR.value())
}

fun Throwable.getEventType(): EventType = when (this) {
    is JuridiskLoggException -> EventType.ERROR_WHILE_SAVING_MESSAGE_IN_JURIDISK_LOGG
    is EncryptionException -> EventType.MESSAGE_ENCRYPTION_FAILED
    is DecryptionException -> EventType.MESSAGE_DECRYPTION_FAILED
    is CompressionException -> EventType.MESSAGE_COMPRESSION_FAILED
    is DecompressionException -> EventType.MESSAGE_DECOMPRESSION_FAILED
    is SignatureException -> EventType.SIGNATURE_CHECK_FAILED
    is CertificateException -> EventType.OCSP_CHECK_FAILED
    else -> EventType.UNKNOWN_ERROR_OCCURRED
}
