package no.nav.emottak.util

import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.SignatureDetailsRequest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader

fun Header.marker(): LogstashMarker {
    val map = mutableMapOf<String,String>()
    map[MARKER_MOTTAK_ID] = this.messageId
    map[MARKER_CONVERSATION_ID] = this.conversationId
    map[CPA_ID] = this.cpaId
    map[SERVICE] = this.service
    map[ACTION] = this.action
    map[TO_ROLE] = this.to.role
    map[FROM_ROLE] = this.from.role
    map[TO_PARTY] = this.to.partyType + ":" + this.to.partyId
    map[FROM_PARTY] = this.from.partyType + ":" + this.from.partyId
    return Markers.appendEntries(map)
}

fun MessageHeader.marker(): LogstashMarker {
    val map = mutableMapOf<String,String>()
    map[MARKER_MOTTAK_ID] = this.messageData.messageId
    map[MARKER_CONVERSATION_ID] = this.conversationId
    map[CPA_ID] = this.cpaId ?: UKJENT_VERDI
    map[SERVICE] = this.service.value ?: UKJENT_VERDI
    map[ACTION] = this.action
    map[TO_ROLE] = this.to.role ?: UKJENT_VERDI
    map[FROM_ROLE] = this.from.role ?: UKJENT_VERDI
    map[TO_PARTY] = (this.to.partyId.firstOrNull()?.type + ":" + this.to.partyId.firstOrNull()?.value)
    map[FROM_PARTY] = (this.from.partyId.firstOrNull()?.type + ":" + this.from.partyId.firstOrNull()?.value)
    return Markers.appendEntries(map)
}

fun SignatureDetailsRequest.marker(): LogstashMarker {
    val map = mutableMapOf<String,String>()
    map[CPA_ID] = this.cpaId
    map[SERVICE] = this.service
    map[ACTION] = this.action
    map[FROM_ROLE] = this.role
    map[FROM_PARTY] = "${this.partyId}:${this.partyId}"
    return Markers.appendEntries(map)
}

private const val PARTY_TYPE_HER = "HER"
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