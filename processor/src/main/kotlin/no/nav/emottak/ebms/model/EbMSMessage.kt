package no.nav.emottak.ebms.model

import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.AckRequested

class EbMSMessage(val ackRequested: AckRequested? = null)
