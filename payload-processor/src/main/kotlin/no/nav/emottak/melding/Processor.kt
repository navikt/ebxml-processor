package no.nav.emottak.melding

import no.nav.emottak.melding.model.Melding
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.melding.model.dekrypter
import no.nav.emottak.melding.process.dekomprimer
import no.nav.emottak.melding.process.komprimer
import no.nav.emottak.melding.process.krypter
import no.nav.emottak.melding.process.signer
import no.nav.emottak.melding.process.verifiserSignatur

class Processor {

    fun process(payloadRequest: PayloadRequest): PayloadResponse {
        return if (payloadRequest.isIncomingMessage()) {
            processIncoming(payloadRequest)
        } else {
            processOutgoing(payloadRequest)
        }
    }

    private fun processIncoming(payloadRequest: PayloadRequest): PayloadResponse {
        val melding = Melding(payloadRequest)
            .dekrypter()
            .dekomprimer()
            //.verifiserXML()
            .verifiserSignatur()
        return PayloadResponse(
            melding.processedPayload
        )
    }

    fun processOutgoing(payloadRequest: PayloadRequest): PayloadResponse {
        val melding = Melding(payloadRequest)
            //.verifiserXML()
            .signer()
            .komprimer()
            .krypter()
        return PayloadResponse(
            melding.processedPayload
        )
    }
}

fun PayloadRequest.isIncomingMessage(): Boolean {
    //TODO
    return true
}