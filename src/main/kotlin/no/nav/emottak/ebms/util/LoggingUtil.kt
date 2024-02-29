package no.nav.emottak.ebms.util

import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.constants.LogIndex
import no.nav.emottak.ebms.model.EbmsMessage
import no.nav.emottak.util.UKJENT_VERDI
import no.nav.emottak.util.marker

fun EbmsMessage.marker(loggableHeaderPairs: List<Pair<String, String>> = emptyList()) : LogstashMarker = Markers.appendEntries(
     mapOf(
        Pair(LogIndex.MARKER_MOTTAK_ID, this.messageId),
        Pair(LogIndex.MARKER_CONVERSATION_ID, this.conversationId),
        Pair(LogIndex.CPA_ID, this.cpaId ?: UKJENT_VERDI),
        Pair(LogIndex.SERVICE, this.addressing.service ?: UKJENT_VERDI),
        Pair(LogIndex.ACTION, this.addressing.action),
        Pair(LogIndex.TO_ROLE, this.addressing.to.role ?: UKJENT_VERDI),
        Pair(LogIndex.FROM_ROLE, this.addressing.from.role ?: UKJENT_VERDI),
        Pair(LogIndex.TO_PARTY, (this.addressing.to.partyId.firstOrNull()?.type + ":" + this.addressing.to.partyId.firstOrNull()?.value)),
        Pair(LogIndex.FROM_PARTY, (this.addressing.from.partyId.firstOrNull()?.type + ":" + this.addressing.from.partyId.firstOrNull()?.value)),
        *loggableHeaderPairs.toTypedArray()
    )
)