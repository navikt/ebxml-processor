package no.nav.emottak.message.model

import no.nav.emottak.message.ebxml.EbXMLConstants
import no.nav.emottak.message.xml.xmlMarshaller
import no.nav.emottak.utils.common.model.Addressing
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Body
import org.xmlsoap.schemas.soap.envelope.ObjectFactory
import java.time.Instant

data class MessageError(
    override val requestId: String,
    override val messageId: String,
    override val refToMessageId: String?,
    override val conversationId: String,
    override val cpaId: String,
    override val addressing: Addressing,
    val feil: List<Feil>,
    override val dokument: Document? = null,
    override val sentAt: Instant? = null

) : EbmsMessage() {

    override fun toEbmsDokument(): EbMSDocument {
        val header = this.createMessageHeader(this.addressing.copy(action = EbXMLConstants.MESSAGE_ERROR_ACTION, service = EbXMLConstants.EBMS_SERVICE_URI))
        return ObjectFactory().createEnvelope()!!.also {
            it.header = header.also {
                it.any.add(this.feil.asErrorList())
            }
            it.body = Body()
        }.let {
            xmlMarshaller.marshal(it)
        }.let {
            EbMSDocument(requestId, it, emptyList())
        }
    }
}

@JvmName("asErrorList")
fun List<Feil>.asErrorList(): ErrorList {
    if (this.isEmpty()) {
        throw IllegalArgumentException("(4.2.3 Kan ikke opprette ErrorList uten errors")
    }

    return this.map {
        it.code.createEbxmlError(it.descriptionText, if (it.severity != null) SeverityType.fromValue(it.severity) else null)
    }.asErrorList()
}

@JvmName("toErrorList")
fun List<org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error>.asErrorList(): ErrorList {
    if (this.isEmpty()) {
        throw IllegalArgumentException("(4.2.3 Kan ikke opprette ErrorList uten errors")
    }
    val errorList = ErrorList()
    errorList.error.addAll(this)
    errorList.version = "2.0"
    errorList.id
    errorList.highestSeverity = this.sortedBy {
        it.severity == SeverityType.ERROR
    }.first().severity
    errorList.isMustUnderstand = true
    return errorList
}
