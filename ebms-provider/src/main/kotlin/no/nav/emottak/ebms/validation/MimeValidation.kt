package no.nav.emottak.ebms.validation

import com.sun.xml.messaging.saaj.soap.ver1_1.SOAPMessageFactory1_1Impl
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import jakarta.xml.soap.SOAPConstants
import jakarta.xml.soap.SOAPFault
import jakarta.xml.soap.SOAPMessage
import no.nav.emottak.ebms.model.EbMSErrorUtil
import java.io.ByteArrayOutputStream
import javax.xml.namespace.QName


object MimeHeaders {
        const val MIME_VERSION                  = "MIME-Version"
        const val SOAP_ACTION                   = "SOAPAction"
        const val CONTENT_TYPE                  = "Content-Type"
        const val CONTENT_ID                    = "Content-Id"
        const val CONTENT_TRANSFER_ENCODING     = "Content-Transfer-Encoding"
        const val CONTENT_DISPOSITION           = "Content-Disposition"
}

object ContentTypeRegex {
        val CONTENT_TYPE = Regex("\\s*^(?<contentType>[^;]+);\\s*")
        val BOUNDARY = Regex("\\s*boundary=\"----=?(?<boundary>[^=\"]+)\"?")
        val START = Regex("\\s*start=\"?(?<start>[^=\"]+)\"?")
        val TYPE = Regex("type=\"?(?<type>[^=\"]+)\"?")
}

/*
fun Headers.validateMime() {
        runCatching {
                validateMimeHeaders()
                validateMultipartAttributter()
        }.onFailure {
                if (it !is MimeValidationException) throw MimeValidationException("Unexpected validation fail.",it) else throw it
        }

}

 */

fun ApplicationRequest.validateMime() {
        runCatching {
                this.headers.validateMimeHeaders()
                this.contentType().validateContentType()
        }.onFailure {
                if (it !is MimeValidationException) throw MimeValidationException("Unexpected validation fail.",it) else throw it
        }
}


// KRAV 5.5.2.2 validate MIME
fun ContentType.validateContentType() {
        if (this == ContentType.Any) throw MimeValidationException("Content  type is undefined")
        if(this.withoutParameters() == ContentType.Text.Xml) return
        if (this.withoutParameters() != ContentType.MultiPart.Related) throw MimeValidationException("Content type should be multipart/related")
        this.parameter("boundary")?:throw MimeValidationException("Boundary is mandatory on multipart related content")
        this.parameter("start")?:throw MimeValidationException("Start on multipart request not defined")
        this.parameter("type") ?: throw MimeValidationException("type of multipart / related is undefined")
        if (this.parameter("type") != "text/xml") throw MimeValidationException("Type of multipart related should be text/xml")
}

//KRAV 5.5.2.3 Valideringsdokument
fun PartData.validateMimeSoapEnvelope() {
        this.contentType?.withoutParameters().takeIf {it == ContentType.parse("text/xml") } ?: throw MimeValidationException("Content type is missing or wrong ")

        this.headers[MimeHeaders.CONTENT_ID].takeUnless { it.isNullOrBlank() }
                ?: throw MimeValidationException("Content ID is missing")
        this.headers["Content-Transfer-Encoding"].takeUnless { it.isNullOrBlank() }?.let {
                it.takeIf {  listOf("8bit","base64","binary","quoted-printable").contains(it) } ?: throw MimeValidationException("Content-Transfer-Encoding should be 8 bit")
        } ?: throw MimeValidationException("Mandatory header Content-Transfer-Encoding is undefined")
}

// Krav 5.5.2.4 Valideringsdokument
fun PartData.validateMimeAttachment() {
        takeIf { this.contentDisposition?.disposition == "attachment"} ?: throw MimeValidationException("This is not attachment")
        takeIf { this.headers[MimeHeaders.CONTENT_TRANSFER_ENCODING] == "base64" } ?: throw MimeValidationException("Feil content transfer encoding")
        this.contentType?.withoutParameters().takeIf {
                it == ContentType.parse("application/pkcs7-mime")
        }?: throw MimeValidationException("Incompatible content type on attachment")
}

// KRAV 5.5.2.1 validate MIME
//private fun Headers.validateMimeHeaders() {
//        // Hvis den er blank så returneres blank streng også er ?: condition true og exception kastes ikke.
//        // Hvis version er null eller blank er den nødvendigvis ikke "1.0" så sjekken er redundant
//        // Hvis version er 2.0, så er eval true, og returnerer null
//        takeUnless { (this[MimeHeaders.MIME_VERSION].isNullOrBlank()).or(this[MimeHeaders.MIME_VERSION] != "1.0") }
//                ?: throw MimeValidationException("MIME version is missing or incorrect")
//        takeUnless { this[MimeHeaders.SOAP_ACTION].isNullOrBlank().or(this[MimeHeaders.SOAP_ACTION] != "ebXML") }
//                ?: throw MimeValidationException("SOAPAction is undefined or incorrect")
//        takeUnless { this[MimeHeaders.CONTENT_TYPE].isNullOrBlank().or(this[MimeHeaders.CONTENT_TYPE] == "text/plain") }
//                ?: throw MimeValidationException("Content  type is wrong")
//}

fun Headers.validateMimeHeaders() {
        if(this[MimeHeaders.MIME_VERSION] != "1.0")
                throw MimeValidationException("MIME version is missing or incorrect")
        if(this[MimeHeaders.SOAP_ACTION] != "ebXML")
                throw MimeValidationException("SOAPAction is undefined or incorrect")
        if(this[MimeHeaders.CONTENT_TYPE].isNullOrBlank() || this[MimeHeaders.CONTENT_TYPE] == "text/plain")
                throw MimeValidationException("Content type is wrong")
}


class MimeValidationException(message:String,cause: Throwable? = null) :
        EbMSErrorUtil.EbxmlProcessException(message, EbMSErrorUtil.createError(EbMSErrorUtil.Code.MIME_PROBLEM, message)
)

fun MimeValidationException.asParseAsSoapFault() : String {
        val faultNs: QName = QName(SOAPConstants.URI_NS_SOAP_ENVELOPE, "Server")
        val message: SOAPMessage = SOAPMessageFactory1_1Impl.newInstance().createMessage()
        val fault: SOAPFault = message.soapBody.addFault()
        fault.setFaultCode(faultNs)
        fault.faultString  = this.message
        val out = ByteArrayOutputStream()
        message.writeTo(out)
        return String(out.toByteArray())
}