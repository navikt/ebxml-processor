package no.nav.emottak.ebms.model

import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.AckRequested
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import java.time.LocalDateTime

class EbMSMessage(override val messageHeader: MessageHeader,
                  val ackRequested: AckRequested? = null,
                  val attachments: List<EbMSAttachment>,
                  val mottatt:  LocalDateTime
                  ) : EbMSBaseMessage
