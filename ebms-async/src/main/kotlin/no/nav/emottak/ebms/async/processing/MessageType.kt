package no.nav.emottak.ebms.async.processing

enum class MessageType(val serviceName: String) {
    HAR_BORGER_FRIKORT_MENGDE("HarBorgerFrikortMengde"),
    INNTEKTSFORESPORSEL("Inntektsforesporsel"),
    TREKKOPPLYSNING("Trekkopplysning"),
    SYKMELDING("Sykmelding"),
    LEGEMELDING("Legemelding"),
    BEHANDLERKRAV("BehandlerKrav"),
    OPPGJORSKONTROLL("OppgjorsKontroll"),
    DIALOGMOTE_INNKALLING("DialogmoteInnkalling"),
    FORESPORSEL_FRA_SAKSBEHANDLER("ForesporselFraSaksbehandler"),
    HENVENDELSE_FRA_LEGE("HenvendelseFraLege"),
    HENVENDELSE_FRA_SAKSBEHANDLER("HenvendelseFraSaksbehandler"),
    OPPFOLGINGSPLAN("Oppfolgingsplan")
}

fun messageTypeByServiceName(serviceName: String): MessageType? {
    return MessageType.entries.firstOrNull { it.serviceName == serviceName }
}
