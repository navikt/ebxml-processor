package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.log

class SignalProcessor {

    fun processSignal(reference: String, content: ByteArray) {
        // TODO do something more useful with acknowledgments and errors
        log.info("Got signal with reference <$reference> and content: ${String(content)}")
    }
}
