package no.nav.emottak.ebms.processing

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.nav.emottak.Event
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.asParseAsSoapFault
import no.nav.emottak.ebms.validation.validateMime

class MimeValidationProcessor(val headers: Headers,
                              val korrelasjonsId: String
): Processor() {
    override fun process() {
        headers.validateMime()
    }

    override fun korrelasjonsId(): String {
        return korrelasjonsId
    }

    override fun persisterHendelse(event: Event): Boolean {
        return true
        //TODO("Not yet implemented")
    }
}