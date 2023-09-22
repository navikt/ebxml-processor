package no.nav.emottak.ebms.model

import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader

class EbmsAcknowledgment(override val messageHeader: MessageHeader,
                         val acknowledgment: Acknowledgment) : EbMSBaseMessage {


}