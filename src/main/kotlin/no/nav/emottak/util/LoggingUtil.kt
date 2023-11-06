package no.nav.emottak.util

import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
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
        Pair(TO_PARTY, "${this.to.partyType}:${this.to.partyId}"),
        Pair(FROM_PARTY, "${this.from.partyType}:${this.from.partyId}"),
    )
)

fun MessageHeader.marker(): LogstashMarker = Markers.appendEntries(
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
    )
)

fun SignatureDetailsRequest.marker(): LogstashMarker = Markers.appendEntries(
    mapOf(
        Pair(CPA_ID, this.cpaId),
        Pair(SERVICE, this.service),
        Pair(ACTION, this.action),
        Pair(FROM_ROLE, this.role),
        Pair(FROM_PARTY, "${this.partyType}:${this.partyId}")
    )
)

private const val UKJENT_VERDI = "Ukjent"

//Kibana log indekser
private const val MARKER_MOTTAK_ID = "mottakId"
private const val MARKER_CONVERSATION_ID = "ebConversationId"
private const val FROM_PARTY = "ebAvsenderId"
private const val TO_PARTY = "ebMottakerId"
private const val TO_ROLE = "toRole"
private const val FROM_ROLE = "fromRole"
private const val SERVICE = "ebxmlService"
private const val CPA_ID = "cpaId"
private const val ACTION = "ebxmlAction"