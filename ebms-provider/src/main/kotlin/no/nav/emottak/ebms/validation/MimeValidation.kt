package no.nav.emottak.ebms.validation

import com.sun.xml.messaging.saaj.soap.ver1_1.SOAPMessageFactory1_1Impl
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.content.PartData
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.contentType
import jakarta.xml.soap.SOAPConstants
import jakarta.xml.soap.SOAPFault
import jakarta.xml.soap.SOAPMessage
import java.io.ByteArrayOutputStream
import javax.xml.namespace.QName

object MimeHeaders {
    const val MIME_VERSION = "MIME-Version"
    const val SOAP_ACTION = "SOAPAction"
    const val CONTENT_TYPE = "Content-Type"
    const val CONTENT_ID = "Content-ID"
    const val CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding"
    const val CONTENT_DISPOSITION = "Content-Disposition"
}

fun ApplicationRequest.validateMime() {
    runCatching {
        this.headers.validateMimeHeaders()
        this.validateContentType()
    }.onFailure {
        when (it) {
            is MimeValidationException -> throw it
            else -> throw MimeValidationException("Unexpected validation fail.", it)
        }
    }
}

// KRAV 5.5.2.2 validate MIME

fun ApplicationRequest.validateContentType() {
    val contentType = this.contentType()
    if (contentType == ContentType.Any) throw MimeValidationException("Content  type is undefined")
    if (contentType.withoutParameters() == ContentType.parse("text/xml")) return

    if (contentType.withoutParameters() != ContentType.parse("multipart/related")) throw MimeValidationException("Content type should be multipart/related")
    contentType.parameter("boundary") ?: throw MimeValidationException("Boundary is mandatory on multipart related content")
    // start blir den første element hvis undefined contentType.parameter("start") ?: throw MimeValidationException("Start on multipart request not defined")
    // norsk helsenet spec sier at type bør vare altid text/xml men det er andre som ikke fyller det.
    if (contentType.parameter("type") != null && contentType.parameter("type") != "text/xml") throw MimeValidationException("Type of multipart related should be text/xml")
}

// KRAV 5.5.2.3 Valideringsdokument
fun PartData.validateMimeSoapEnvelope() {
    this.contentType?.withoutParameters().takeIf { it == ContentType.parse("text/xml") } ?: throw MimeValidationException("Content type is missing or wrong ")
    // TODO Kontakt EPJ der Content ID mangler
//    if (this.headers[MimeHeaders.CONTENT_ID].isNullOrBlank()) {
//        throw MimeValidationException("Content ID is missing")
//    }
    this.headers[MimeHeaders.CONTENT_TRANSFER_ENCODING].takeUnless { it.isNullOrBlank() }?.let {
        it.takeIf { listOf("8bit", "base64", "binary", "quoted-printable").contains(it) } ?: throw MimeValidationException("Unrecognised Content-Transfer-Encoding")
    } ?: throw MimeValidationException("Mandatory header Content-Transfer-Encoding is undefined")
}

// Krav 5.5.2.4 Valideringsdokument
fun PartData.validateMimeAttachment() {
    this.contentType?.withoutParameters().takeIf {
        it == ContentType.parse("application/pkcs7-mime")
    }?.apply {
        this@validateMimeAttachment.headers[MimeHeaders.CONTENT_TRANSFER_ENCODING].takeIf { it == "base64" } ?: throw MimeValidationException("Feil content transfer encoding på kryptert content.")
    }
}

// KRAV 5.5.2.1 validate MIME
fun Headers.validateMimeHeaders() {
    if (this[MimeHeaders.CONTENT_TYPE].isNullOrBlank() || this[MimeHeaders.CONTENT_TYPE] == "text/plain") {
        throw MimeValidationException("Content type is wrong <${this[MimeHeaders.CONTENT_TYPE]}")
    }
}

class MimeValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun Exception.parseAsSoapFault(extraMessage: String? = null): String {
    val faultNs: QName = QName(SOAPConstants.URI_NS_SOAP_ENVELOPE, "Server")
    val message: SOAPMessage = SOAPMessageFactory1_1Impl.newInstance().createMessage()
    val fault: SOAPFault = message.soapBody.addFault()
    fault.setFaultCode(faultNs)
    fault.faultString = extraMessage?.let {
        it + ": " + this.message
    } ?: this.message
    val out = ByteArrayOutputStream()
    message.writeTo(out)
    return String(out.toByteArray())
}
