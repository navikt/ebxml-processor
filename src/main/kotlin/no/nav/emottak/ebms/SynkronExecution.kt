package no.nav.emottak.ebms

import io.ktor.client.plugins.ResponseException
import no.nav.emottak.ebms.model.Payload
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.PayloadProcessing
import no.nav.emottak.melding.model.asErrorList
import java.util.*

class SynkronExecution(private val ebmsMessage: PayloadMessage, val validationService: DokumentValidator, val processingService: ProcessingService, val sendInService: SendInService) {
    private lateinit var ebmsProcessing: EbmsProcessing
    private lateinit var payloadProcessing: PayloadProcessing
    private lateinit var processedPayload: ByteArray
    private lateinit var replayTo: Addressing
    private lateinit var responseMessage: ByteArray
    private var executionFeil: MutableList<Feil> = mutableListOf()
    private lateinit var responsePayloadProcessing: PayloadProcessing

    fun exchange(ebmsMessage: PayloadMessage): EbmsMessage? {
        validationService.validateIn2(ebmsMessage)
            .also {
                if (it.ebmsProcessing != null) ebmsProcessing = it.ebmsProcessing!!
                if (it.payloadProcessing != null) payloadProcessing = it.payloadProcessing!!
            }.also {
                if (!it.valid()) {
                    return ebmsMessage.createFail(it.error!!)
                }
                if (!::payloadProcessing.isInitialized) {
                    return ebmsMessage.createFail(
                        listOf(
                            Feil(
                                ErrorCode.DELIVERY_FAILURE,
                                "Missing processing information for role servide action ${ebmsMessage.addressing.to.role} ${ebmsMessage.addressing.service} ${ebmsMessage.addressing.action}"
                            )
                        )
                    )
                }
            }
        try {
            processingService.processSyncIn2(ebmsMessage, payloadProcessing)
        } catch (ex: EbmsException) {
            // @TODO fix logger().error(ebmsMessage.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
            logger().error("Processing failed: ${ex.message}", ex)
            return ebmsMessage
                .createFail(ex.feil)
        } catch (ex: ResponseException) {
            // @TODO fix  logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
            logger().error("Processing failed: ${ex.message}", ex)
            return ebmsMessage
                .createFail(listOf(Feil(ErrorCode.UNKNOWN, "Processing failed: ${ex.message}")))
        }
        runCatching {
            sendInService.sendIn(ebmsMessage, ebmsProcessing, processedPayload)
        }.onSuccess {
            replayTo = it.addressing
            responseMessage = it.payload
        }.onFailure {
            return ebmsMessage.createFail(listOf(Feil(ErrorCode.DELIVERY_FAILURE, it.localizedMessage)))
        }
        var responseMessage = PayloadMessage(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            ebmsMessage.conversationId,
            ebmsMessage.cpaId,
            replayTo,
            Payload(responseMessage, "text/xml", UUID.randomUUID().toString())
        )
        runCatching {
            validationService.validateOut(UUID.randomUUID().toString(), responseMessage).also {
                if (it.payloadProcessing != null) responsePayloadProcessing = it.payloadProcessing!!
            }.also {
                if (!it.valid()) {
                    return ebmsMessage.createFail(it.error!!)
                }
                if (!::payloadProcessing.isInitialized) {
                    return ebmsMessage.createFail(
                        listOf(
                            Feil(
                                ErrorCode.DELIVERY_FAILURE,
                                "Missing processing information for role servide action ${ebmsMessage.addressing.to.role} ${ebmsMessage.addressing.service} ${ebmsMessage.addressing.action}"
                            )
                        )
                    )
                }
            }
        }.onFailure {
            return ebmsMessage.createFail(
                listOf(
                    Feil(
                        ErrorCode.OTHER_XML,
                        "Unknown error during response message validation."
                    )
                )
            )
        }
        runCatching {
            processingService.proccessSyncOut2(responseMessage, responsePayloadProcessing)
        }.onFailure {
            if (it is EbmsException) {
                return ebmsMessage
                    .createFail(it.feil)
            } else {
                ebmsMessage
                    .createFail(listOf(Feil(ErrorCode.UNKNOWN, "Processing failed: ${it.message}")))
            }
        }.onSuccess {
            it
            return responseMessage.copy(payload = responseMessage.payload.copy(payload = (it as PayloadMessage).payload.payload))
        }

        return null
    }
}
