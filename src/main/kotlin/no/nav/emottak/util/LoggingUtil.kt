package no.nav.emottak.util

import io.ktor.http.Headers
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.constants.LogIndex.ACTION
import no.nav.emottak.constants.LogIndex.CPA_ID
import no.nav.emottak.constants.LogIndex.FROM_PARTY
import no.nav.emottak.constants.LogIndex.FROM_ROLE
import no.nav.emottak.constants.LogIndex.MARKER_CONVERSATION_ID
import no.nav.emottak.constants.LogIndex.MARKER_MOTTAK_ID
import no.nav.emottak.constants.LogIndex.SERVICE
import no.nav.emottak.constants.LogIndex.TO_PARTY
import no.nav.emottak.constants.LogIndex.TO_ROLE
import no.nav.emottak.constants.LogIndex.X_MAILER
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.SignatureDetailsRequest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader

fun Header.marker(): LogstashMarker = Markers.appendEntries(
    mapOf(
        Pair(MARKER_MOTTAK_ID, this.messageId),
        Pair(MARKER_CONVERSATION_ID, this.conversationId),
        Pair(CPA_ID, this.cpaId),
        Pair(SERVICE, this.service),
        Pair(ACTION, this.action),
        Pair(TO_ROLE, this.to.role),
        Pair(FROM_ROLE, this.from.role),
        Pair(TO_PARTY, "${this.to.partyId.firstOrNull()?.type ?: UKJENT_VERDI}:${this.to.partyId.firstOrNull()?.value ?: UKJENT_VERDI}"),
        Pair(FROM_PARTY, "${this.from.partyId.firstOrNull()?.type ?: UKJENT_VERDI}:${this.from.partyId.firstOrNull()?.value ?: UKJENT_VERDI}"),
    )
)

fun MessageHeader.marker(loggableHeaderPairs: List<Pair<String, String>> = emptyList()): LogstashMarker = Markers.appendEntries(
    mapOf(
        Pair(MARKER_MOTTAK_ID, this.messageData.messageId),
        Pair(MARKER_CONVERSATION_ID, this.conversationId),
        Pair(CPA_ID, this.cpaId ?: UKJENT_VERDI),
        Pair(SERVICE, this.service.value ?: UKJENT_VERDI),
        Pair(ACTION, this.action),
        Pair(TO_ROLE, this.to.role ?: UKJENT_VERDI),
        Pair(FROM_ROLE, this.from.role ?: UKJENT_VERDI),
        Pair(TO_PARTY, (this.to.partyId.firstOrNull()?.type + ":" + this.to.partyId.firstOrNull()?.value)),
        Pair(FROM_PARTY, (this.from.partyId.firstOrNull()?.type + ":" + this.from.partyId.firstOrNull()?.value)),
        *loggableHeaderPairs.toTypedArray()
    )
)

fun Headers.marker(): LogstashMarker = Markers.appendEntries(
    this.retrieveLoggableHeaderPairs().toMap()
)
fun Headers.retrieveLoggableHeaderPairs(): List<Pair<String, String>> {
    return listOf(
        Pair(X_MAILER, this[SMTPHeaders.X_MAILER] ?: "-"),
        Pair("smtpMessageId", this[SMTPHeaders.MESSAGE_ID]!!)
    )
}

fun SignatureDetailsRequest.marker(): LogstashMarker = Markers.appendEntries(
    mapOf(
        Pair(CPA_ID, this.cpaId),
        Pair(SERVICE, this.service),
        Pair(ACTION, this.action),
        Pair(FROM_ROLE, this.role),
        Pair(FROM_PARTY, "${this.partyType}:${this.partyId}")
    )
)

private const val UKJENT_VERDI = "Ukjent" // Egentlig null