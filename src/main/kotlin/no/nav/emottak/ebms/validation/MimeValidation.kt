package no.nav.emottak.ebms.validation

import com.sun.xml.messaging.saaj.soap.ver1_1.SOAPMessageFactory1_1Impl
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import jakarta.xml.soap.SOAPConstants
import jakarta.xml.soap.SOAPFault
import jakarta.xml.soap.SOAPMessage
import java.io.ByteArrayOutputStream
import javax.xml.namespace.QName
import kotlin.streams.asStream


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
                this.validateContentType()
        }.onFailure {
                if (it !is MimeValidationException) throw MimeValidationException("Unexpected validation fail.",it) else throw it
        }
}


// KRAV 5.5.2.2 validate MIME

fun ApplicationRequest.validateContentType() {
        val contentType = this.contentType()
        if (contentType == ContentType.Any) throw MimeValidationException("Content  type is undefined")
        if(contentType.withoutParameters() == ContentType.parse("text/xml")) return

        if (contentType.withoutParameters() != ContentType.parse("multipart/related")) throw MimeValidationException("Content type should be multipart/related")
        contentType.parameter("boundary")?:throw MimeValidationException("Boundary is mandatory on multipart related content")
        contentType.parameter("start")?:throw MimeValidationException("Start on multipart request not defined")
        contentType.parameter("type") ?: throw MimeValidationException("type of multipart / related is undefined")
        if (contentType.parameter("type") != "text/xml") throw MimeValidationException("Type of multipart related should be text/xml")
}

//KRAV 5.5.2.3 Valideringsdokument
fun PartData.validateMimeSoapEnvelope() {
        this.contentType?.takeIf {
                        it.contentType + "/" + it.contentSubtype == "text/xml" } ?: throw MimeValidationException("Content type is missing or wrong ")

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
        this.contentType?.takeIf {
                it.contentType + "/" + it.contentSubtype == "application/pkcs7-mime"
        }?: throw MimeValidationException("Incompatible content type on attachment")
}

// KRAV 5.5.2.1 validate MIME
private fun Headers.validateMimeHeaders() {
        takeUnless { (this[MimeHeaders.MIME_VERSION].isNullOrBlank()).or(this[MimeHeaders.MIME_VERSION] != "1.0") }
                ?: throw MimeValidationException("MIME version is missing or incorrect")
        takeUnless { this[MimeHeaders.SOAP_ACTION].isNullOrBlank().or(this[MimeHeaders.SOAP_ACTION] != "ebXML") }
                ?: throw MimeValidationException("SOAPAction is undefined or incorrect")
        takeUnless { this[MimeHeaders.CONTENT_TYPE].isNullOrBlank().or(this[MimeHeaders.CONTENT_TYPE] == "text/plain") }
                ?: throw MimeValidationException("Content  type is wrong")
}






fun String.parseContentType(): String {
        if (this == "text/xml") return this
        return ContentTypeRegex.CONTENT_TYPE.find(this)?.groups?.get("contentType")?.value ?: throw MimeValidationException("Missing content-type")
}

class MimeValidationException(message:String,cause: Throwable? = null) : Exception(message,cause)

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