package no.nav.emottak.melding

import no.nav.emottak.melding.model.Melding
import no.nav.emottak.melding.process.dekrypter
import no.nav.emottak.melding.process.krypter
import no.nav.emottak.melding.process.signer
import no.nav.emottak.melding.process.verifiserSignatur

class Processor {

    fun processIncoming(melding: Melding): Melding {
        return melding
            .dekrypter()
            //.unzip()
            //.verifiserXML()
            .verifiserSignatur()
    }

    fun processOutgoing(melding: Melding): Melding {
        return melding
            //.verifiserXML()
            .signer()
            //.zip()
            .krypter()
    }
}