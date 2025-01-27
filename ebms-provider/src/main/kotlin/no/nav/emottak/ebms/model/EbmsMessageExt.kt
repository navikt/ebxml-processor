package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbmsFail
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.log
import no.nav.emottak.message.model.toEbmsMessageDetails
import java.sql.SQLException

fun EbmsFail.sjekkSignature(signatureDetails: SignatureDetails) {
    SignaturValidator.validate(signatureDetails, this.dokument!!, listOf())
    no.nav.emottak.message.model.log.info("Signatur OK")
}

fun Acknowledgment.sjekkSignature(signatureDetails: SignatureDetails) {
    SignaturValidator.validate(signatureDetails, this.dokument!!, listOf())
    log.info("Signatur OK")
}

fun PayloadMessage.sjekkSignature(signatureDetails: SignatureDetails) {
    SignaturValidator.validate(signatureDetails, this.dokument!!, listOf(this.payload))
    no.nav.emottak.message.model.log.info("Signatur OK")
}

fun EbmsMessage.sjekkSignature(signatureDetails: SignatureDetails) {
    SignaturValidator.validate(signatureDetails, this.dokument!!, if (this is PayloadMessage) listOf(this.payload) else listOf())
    log.info("Signatur OK")
}

fun EbmsMessageDetailsRepository.saveEbmsMessage(
    ebmsMessage: EbmsMessage
) {
    val ebmsMessageDetails = ebmsMessage.toEbmsMessageDetails()
    val markers = ebmsMessage.marker()
    try {
        this.saveEbmsMessageDetails(ebmsMessageDetails).also {
            if (it == null) {
                no.nav.emottak.ebms.log.info(markers, "Message details has not been saved to database")
            } else {
                no.nav.emottak.ebms.log.info(markers, "Message details saved to database")
            }
        }
    } catch (ex: SQLException) {
        val hint = this.handleSQLException(ex)
        no.nav.emottak.ebms.log.error(markers, "SQL exception ${ex.sqlState} occurred while saving message details to database: $hint", ex)
    } catch (ex: Exception) {
        no.nav.emottak.ebms.log.error(markers, "Error occurred while saving message details to database", ex)
    }
}
