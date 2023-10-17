package no.nav.emottak.util

import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.melding.model.Header
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
    map[TO_HER_ID] = this.to.herID
    map[FROM_HER_ID] = this.from.herID
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
    map[TO_HER_ID] = this.to.partyId.firstOrNull{ it.type == PARTY_TYPE_HER }?.value ?: UKJENT_VERDI
    map[FROM_HER_ID] = this.from.partyId.firstOrNull{ it.type == PARTY_TYPE_HER }?.value ?: UKJENT_VERDI
    return Markers.appendEntries(map)
}

private const val PARTY_TYPE_HER = "HER"
private const val UKJENT_VERDI = "Ukjent"

//Kibana log indekser
private const val MARKER_MOTTAK_ID = "mottakId"
private const val MARKER_CONVERSATION_ID = "ebConversationId"
private const val FROM_HER_ID = "ebAvsenderId"
private const val TO_HER_ID = "ebMottakerId"
private const val TO_ROLE = "toRole"
private const val FROM_ROLE = "fromRole"
private const val SERVICE = "ebxmlService"
private const val CPA_ID = "cpaId"
private const val ACTION = "ebxmlAction"