package no.nav.emottak.util

import io.ktor.http.Headers
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.constants.LogIndex.ACTION
import no.nav.emottak.constants.LogIndex.CPA_ID
import no.nav.emottak.constants.LogIndex.FROM_PARTY
import no.nav.emottak.constants.LogIndex.FROM_ROLE
import no.nav.emottak.constants.LogIndex.CONVERSATION_ID
import no.nav.emottak.constants.LogIndex.MESSAGE_ID
import no.nav.emottak.constants.LogIndex.SERVICE
import no.nav.emottak.constants.LogIndex.TO_PARTY
import no.nav.emottak.constants.LogIndex.TO_ROLE
import no.nav.emottak.constants.LogIndex.X_MAILER
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.melding.model.SignatureDetailsRequest
import no.nav.emottak.melding.model.ValidationRequest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader

fun Header.marker(): LogstashMarker = Markers.appendEntries(
    mapOf(
        MESSAGE_ID to this.messageId,
        CONVERSATION_ID to this.conversationId,
        CPA_ID to this.cpaId,
        SERVICE to this.service,
        ACTION to this.action,
        TO_ROLE to this.to.role,
        FROM_ROLE to this.from.role,
        TO_PARTY to "${this.to.partyId.firstOrNull()?.type ?: UKJENT_VERDI}:${this.to.partyId.firstOrNull()?.value ?: UKJENT_VERDI}",
        FROM_PARTY to "${this.from.partyId.firstOrNull()?.type ?: UKJENT_VERDI}:${this.from.partyId.firstOrNull()?.value ?: UKJENT_VERDI}",
    )
)

fun PayloadRequest.marker(): LogstashMarker = Markers.appendEntries(
    mapOf(
        MESSAGE_ID to this.messageId,
        CONVERSATION_ID to this.conversationId,
    )
)

fun SendInRequest.marker(): LogstashMarker = Markers.appendEntries(
    mapOf(
        MESSAGE_ID to this.messageId,
        CONVERSATION_ID to this.conversationId,
    )
)

fun ValidationRequest.marker(): LogstashMarker = Markers.appendEntries(
    mapOf(
        MESSAGE_ID to this.messageId,
        CONVERSATION_ID to this.conversationId,
        CPA_ID to this.cpaId,
        SERVICE to this.addressing.service,
        ACTION to this.addressing.action,
        TO_ROLE to this.addressing.to.role,
        FROM_ROLE to this.addressing.from.role,
        TO_PARTY to "${this.addressing.to.partyId.firstOrNull()?.type ?: UKJENT_VERDI}:${this.addressing.to.partyId.firstOrNull()?.value ?: UKJENT_VERDI}",
        FROM_PARTY to "${this.addressing.from.partyId.firstOrNull()?.type ?: UKJENT_VERDI}:${this.addressing.from.partyId.firstOrNull()?.value ?: UKJENT_VERDI}",
    )
)

fun MessageHeader.marker(loggableHeaderPairs: Map<String, String> = mapOf()): LogstashMarker = Markers.appendEntries(
    mapOf(
        MESSAGE_ID to this.messageData.messageId,
        CONVERSATION_ID to this.conversationId,
        CPA_ID to (this.cpaId ?: UKJENT_VERDI),
        SERVICE to (this.service.value ?: UKJENT_VERDI),
        ACTION to this.action,
        TO_ROLE to (this.to.role ?: UKJENT_VERDI),
        FROM_ROLE to (this.from.role ?: UKJENT_VERDI),
        TO_PARTY to "${this.to.partyId.firstOrNull()?.type}:${this.to.partyId.firstOrNull()?.value}",
        FROM_PARTY to "${this.from.partyId.firstOrNull()?.type}:${this.from.partyId.firstOrNull()?.value}"
    ) + loggableHeaderPairs
)


fun Headers.marker(): LogstashMarker = Markers.appendEntries(
    this.retrieveLoggableHeaderPairs()
)

fun Headers.retrieveLoggableHeaderPairs(): Map<String, String> {
    return mapOf(
        X_MAILER to (this[SMTPHeaders.X_MAILER] ?: "-"),
        "messageId" to (this[SMTPHeaders.MESSAGE_ID] ?: this["X-Request-Id"] ?: "-")
    )
}

fun SignatureDetailsRequest.marker(): LogstashMarker = Markers.appendEntries(
    mapOf(
        CPA_ID to this.cpaId,
        SERVICE to this.service,
        ACTION to this.action,
        FROM_ROLE to this.role,
        FROM_PARTY to "${this.partyType}:${this.partyId}"
    )
)

const val UKJENT_VERDI = "Ukjent" // Egentlig null
