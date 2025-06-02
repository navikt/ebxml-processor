package no.nav.emottak.message.model

import javax.xml.soap.SOAPFault

data class EbxmlProcessingResponse(
    val processingResponse: EbmsMessage,
    val ebmsProcessing: EbmsProcessing = EbmsProcessing(),
    val ebxmlFail: MessageError,
    val soapFault: SOAPFault
)
