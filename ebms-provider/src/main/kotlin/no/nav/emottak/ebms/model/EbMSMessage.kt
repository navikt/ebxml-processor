package no.nav.emottak.ebms.model

import no.nav.emottak.Event
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.AckRequested
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import java.time.LocalDateTime

class EbMSMessage(override val messageHeader: MessageHeader,
                  val ackRequested: AckRequested? = null,
                  val attachments: List<EbMSAttachment>,
                  val mottatt:  LocalDateTime
                  ) : EbMSBaseMessage
{
    private val eventLogg: ArrayList<Event> = ArrayList() // TODO tenk over
    fun addHendelse(event: Event) {
        eventLogg.add(event)
    }

    fun harHendelse(event: Event): Boolean {
        return eventLogg.contains(event)
    }

    open fun type(): Type {
        return Type.PAYLOAD
    }

    enum class Type {
        ACK, ERROR, PAYLOAD
    }

}
