package no.nav.emottak.melding

import no.nav.emottak.melding.model.Melding
import no.nav.emottak.melding.process.dekomprimer
import no.nav.emottak.melding.process.dekrypter
import no.nav.emottak.melding.process.komprimer
import no.nav.emottak.melding.process.krypter
import no.nav.emottak.melding.process.signer
import no.nav.emottak.melding.process.verifiserSignatur

class Processor {

    fun processIncoming(melding: Melding): Melding {
        return melding
            .dekrypter()
            .dekomprimer()
            //.verifiserXML()
            .verifiserSignatur()
    }

    fun processOutgoing(melding: Melding): Melding {
        return melding
            //.verifiserXML()
            .signer()
            .komprimer()
            .krypter()
    }
}