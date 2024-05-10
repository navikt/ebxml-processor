package no.nav.emottak.ebms.util

import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.constants.LogIndex
import no.nav.emottak.ebms.model.EbmsMessage

fun EbmsMessage.marker(loggableHeaderPairs: Map<String, String> = emptyMap()): LogstashMarker = Markers.appendEntries(
    mapOf(
        LogIndex.MESSAGE_ID to this.messageId,
        LogIndex.CONVERSATION_ID to this.conversationId,
        LogIndex.CPA_ID to this.cpaId,
        LogIndex.SERVICE to this.addressing.service,
        LogIndex.ACTION to this.addressing.action,
        LogIndex.TO_ROLE to this.addressing.to.role,
        LogIndex.FROM_ROLE to this.addressing.from.role,
        LogIndex.TO_PARTY to (this.addressing.to.partyId.firstOrNull()?.type + ":" + this.addressing.to.partyId.firstOrNull()?.value),
        LogIndex.FROM_PARTY to (this.addressing.from.partyId.firstOrNull()?.type + ":" + this.addressing.from.partyId.firstOrNull()?.value)
    ) + loggableHeaderPairs
)
