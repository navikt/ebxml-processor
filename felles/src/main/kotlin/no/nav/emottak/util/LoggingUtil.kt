package no.nav.emottak.util

import io.ktor.http.Headers
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.Header
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.SignatureDetailsRequest
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.utils.common.constants.LogFields.ACTION
import no.nav.emottak.utils.common.constants.LogFields.CONVERSATION_ID
import no.nav.emottak.utils.common.constants.LogFields.CPA_ID
import no.nav.emottak.utils.common.constants.LogFields.FROM_PARTY
import no.nav.emottak.utils.common.constants.LogFields.FROM_ROLE
import no.nav.emottak.utils.common.constants.LogFields.MESSAGE_ID
import no.nav.emottak.utils.common.constants.LogFields.SERVICE
import no.nav.emottak.utils.common.constants.LogFields.TO_PARTY
import no.nav.emottak.utils.common.constants.LogFields.TO_ROLE
import no.nav.emottak.utils.common.constants.LogFields.X_MAILER
import no.nav.emottak.utils.common.constants.LogFields.X_REQUEST_ID
import no.nav.emottak.utils.common.model.SendInRequest
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
        FROM_PARTY to "${this.from.partyId.firstOrNull()?.type ?: UKJENT_VERDI}:${this.from.partyId.firstOrNull()?.value ?: UKJENT_VERDI}"
    )
)

fun PayloadRequest.marker(): LogstashMarker = Markers.appendEntries(
    mapOf(
        MESSAGE_ID to this.messageId,
        CONVERSATION_ID to this.conversationId,
        SERVICE to this.addressing.service,
        ACTION to this.addressing.action,
        TO_ROLE to this.addressing.to.role,
        FROM_ROLE to this.addressing.from.role,
        TO_PARTY to "${this.addressing.to.partyId.firstOrNull()?.type ?: UKJENT_VERDI}:${this.addressing.to.partyId.firstOrNull()?.value ?: UKJENT_VERDI}",
        FROM_PARTY to "${this.addressing.from.partyId.firstOrNull()?.type ?: UKJENT_VERDI}:${this.addressing.from.partyId.firstOrNull()?.value ?: UKJENT_VERDI}"
    )
)

fun SendInRequest.marker(): LogstashMarker = Markers.appendEntries(
    mapOf(
        MESSAGE_ID to this.messageId,
        CONVERSATION_ID to this.conversationId
    )
)

fun EbmsMessage.marker(loggableHeaderPairs: Map<String, String> = emptyMap()): LogstashMarker = Markers.appendEntries(
    mapOf(
        X_REQUEST_ID to this.requestId,
        MESSAGE_ID to this.messageId,
        CONVERSATION_ID to this.conversationId,
        CPA_ID to this.cpaId,
        SERVICE to this.addressing.service,
        ACTION to this.addressing.action,
        TO_ROLE to this.addressing.to.role,
        FROM_ROLE to this.addressing.from.role,
        TO_PARTY to (this.addressing.to.partyId.firstOrNull()?.type + ":" + this.addressing.to.partyId.firstOrNull()?.value),
        FROM_PARTY to (this.addressing.from.partyId.firstOrNull()?.type + ":" + this.addressing.from.partyId.firstOrNull()?.value)
    ) + loggableHeaderPairs
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
        FROM_PARTY to "${this.addressing.from.partyId.firstOrNull()?.type ?: UKJENT_VERDI}:${this.addressing.from.partyId.firstOrNull()?.value ?: UKJENT_VERDI}"
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
        "mimeMessageId" to (this[SMTPHeaders.MESSAGE_ID] ?: "-"),
        X_REQUEST_ID to (this[SMTPHeaders.X_REQUEST_ID] ?: "-")
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
