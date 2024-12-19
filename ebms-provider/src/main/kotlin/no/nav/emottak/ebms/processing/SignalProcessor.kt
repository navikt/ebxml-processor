package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.log

class SignalProcessor {

    fun processSignal(signal: Pair<String, ByteArray>) {
        log.info("Got signal with reference <${signal.first}> and content: ${String(signal.second)}")
    }
}
