package no.nav.emottak.ebms.processing

fun interface Processor {
    fun process() // TODO kan sikkert ta imot en context. EbmsMessageContext?
}