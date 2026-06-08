package no.nav.emottak.ebms.async.processing

enum class MessageType(val serviceName: String) {
    HAR_BORGER_FRIKORT_MENGDE("HarBorgerFrikortMengde"),
    INNTEKTSFORESPORSEL("Inntektsforesporsel"),
    TREKKOPPLYSNING("Trekkopplysning"),
    SYKMELDING("Sykmelding"),
    LEGEMELDING("Legemelding")
}

fun messageTypeByServiceName(serviceName: String): MessageType? {
    return MessageType.entries.firstOrNull { it.serviceName == serviceName }
}
