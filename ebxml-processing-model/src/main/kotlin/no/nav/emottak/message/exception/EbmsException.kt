package no.nav.emottak.message.exception

import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType

open class EbmsException(
    val feil: List<Feil>,
    cause: Throwable? = null
) : Exception(concatFeilmessage(feil), cause) {

    constructor(
        message: String,
        errorCode: ErrorCode = ErrorCode.UNKNOWN,
        severity: String = SeverityType.ERROR.value()!!,
        recoverable: Boolean = true,
        cause: Throwable? = null
    ) : this(listOf(Feil(errorCode, message, severity, recoverable)), cause)

    companion object {
        fun concatFeilmessage(feil: List<Feil>) =
            feil.joinToString(separator = ",") {
                it.descriptionText
            }
    }

    fun isRecoverable() = feil.all { it.recoverable }
}
