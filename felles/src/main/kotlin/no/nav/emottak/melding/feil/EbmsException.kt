package no.nav.emottak.melding.feil

import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType


open class EbmsException(
    val feil: List<Feil>,
    exception: Throwable? = null
) : Exception( concatFeilmessage(feil),exception) {

     constructor(
        message: String,
        errorCode: ErrorCode = ErrorCode.UNKNOWN,
        severity: String = SeverityType.ERROR.value()!!,
        exception: Throwable? = null
    ) : this( listOf(Feil(errorCode, message, severity)), exception)

    companion object {
        fun concatFeilmessage(feil:List<Feil>) =
                feil.joinToString(separator = ",") {
                    it.descriptionText
                }
    }



}
