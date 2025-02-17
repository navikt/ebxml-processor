package no.nav.emottak.ebms.processing

import io.ktor.http.ContentType
import no.nav.emottak.ebms.log
import no.nav.emottak.ebms.messaging.EbmsPayloadProducer
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.persistence.repository.PayloadRepository
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.message.model.AsyncPayload
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.xml.asByteArray
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PayloadMessageResponder(
    val sendInService: SendInService,
    val validator: DokumentValidator,
    val processingService: ProcessingService,
    val payloadRepository: PayloadRepository,
    val ebmsPayloadProducer: EbmsPayloadProducer
) {

    @OptIn(ExperimentalUuidApi::class)
    suspend fun respond(payloadMessage: PayloadMessage) {
        try {
            sendInService.sendIn(payloadMessage).let {
                PayloadMessage(
                    requestId = Uuid.random().toString(),
                    messageId = Uuid.random().toString(),
                    conversationId = it.conversationId,
                    cpaId = payloadMessage.cpaId,
                    addressing = it.addressing,
                    payload = Payload(it.payload, ContentType.Application.Xml.toString()),
                    refToMessageId = it.messageId
                )
            }.let { payloadMessageResponse ->
                Pair(payloadMessageResponse, validator.validateOut(payloadMessageResponse).payloadProcessing)
            }.let { messageProcessing ->
                val processedMessage =
                    processingService.proccessSyncOut(messageProcessing.first, messageProcessing.second)
                Pair(processedMessage, messageProcessing.second)
            }.let {
                it.first.toEbmsDokument().also { ebmsDocument ->
                    ebmsDocument.signer(it.second!!.signingCertificate)
                }.let {
                    it.attachments.forEach { payload ->
                        payloadRepository.updateOrInsert(
                            AsyncPayload(
                                referenceId = it.requestId,
                                contentId = payload.contentId,
                                contentType = payload.contentType,
                                content = payload.bytes
                            )
                        )
                    }
                    ebmsPayloadProducer.send(it.requestId, it.dokument.asByteArray())
                }
                log.info(it.first.marker(), "Payload message response returned successfully")
            }
        } catch (e: Exception) {
            log.error(payloadMessage.marker(), "Error processing asynchronous payload response", e)
        }
    }
}
