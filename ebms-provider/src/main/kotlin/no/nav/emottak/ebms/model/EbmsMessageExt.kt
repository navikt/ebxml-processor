package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.persistence.repository.EventsRepository
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.Event
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.log
import no.nav.emottak.message.model.toEbmsMessageDetails
import java.sql.SQLException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun EbmsMessage.sjekkSignature(signatureDetails: SignatureDetails) {
    SignaturValidator.validate(signatureDetails, this.dokument!!, if (this is PayloadMessage) listOf(this.payload) else listOf())
    log.info("Signatur OK")
}

@OptIn(ExperimentalUuidApi::class)
fun EbmsMessage.saveEvent(message: String, eventsRepository: EventsRepository) {
    log.info(this.marker(), message)
    eventsRepository.updateOrInsert(
        Event(
            eventId = Uuid.random(),
            referenceId = UUID.fromString(this.requestId),
            messageId = this.messageId,
            eventMessage = message
        )
    )
}

@OptIn(ExperimentalUuidApi::class)
fun EbmsMessageDetailsRepository.saveEbmsMessage(
    ebmsMessage: EbmsMessage
) {
    val markers = ebmsMessage.marker()
    try {
        this.saveEbmsMessageDetails(ebmsMessage.toEbmsMessageDetails()).also {
            if (it == null) {
                log.info(markers, "Message details has not been saved to database")
            } else {
                log.info(markers, "Message details saved to database")
            }
        }
    } catch (ex: SQLException) {
        val hint = this.handleSQLException(ex)
        log.error(markers, "SQL exception ${ex.sqlState} occurred while saving message details to database: $hint", ex)
    } catch (ex: Exception) {
        log.error(markers, "Error occurred while saving message details to database", ex)
    }
}
