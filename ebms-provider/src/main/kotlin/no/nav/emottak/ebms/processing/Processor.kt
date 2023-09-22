package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSMessage

fun interface Processor {
    fun process(ebMSMessage: EbMSMessage) // TODO kan sikkert ta imot en context. EbmsMessageContext?
}