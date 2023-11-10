package no.nav.emottak.melding.feil

import no.nav.emottak.melding.model.ErrorCode


open class EbmsException(val descriptionText:String,
                         val errorCode: ErrorCode = ErrorCode.UNKNOWN,
                         val severity: String,
                         exception: java.lang.Exception?) : Exception(descriptionText,exception)
