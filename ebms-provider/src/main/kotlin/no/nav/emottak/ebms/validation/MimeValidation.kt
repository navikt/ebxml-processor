package no.nav.emottak.ebms.validation

import io.ktor.http.*
import kotlin.streams.asStream


object MimeHeaders {
        const val MIME_VERSION   = "MIME-Version"
        const val SOAP_ACTION    = "SOAPAction"
        const val CONTENT_TYPE   = "Content-Type"
}

object ContentTypeRegex {
        val CONTEN_TYPE = Regex("^(?<contentType>[^;]+);\\s*")
        val BOUNDARY = Regex("\\s*boundary=\"----=?(?<boundary>[^=\"]+)\"?;")
        val START = Regex("\\s*start=\"?(?<start>[^=\"]+)\"?;")
        val TYPE = Regex("type=\"?(?<type>[^=\"]+)\"?")
}



fun Headers.validateMime() {

                validateMimeHeaders()
                validateMultipartAttributter()


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

// KRAV 5.5.2.2 validate MIME
private fun Headers.validateMultipartAttributter() {
        val contentTypeHeader = this[MimeHeaders.CONTENT_TYPE]!!

        if (contentTypeHeader != "text/xml")  {

                val contentType = ContentTypeRegex.CONTEN_TYPE.find(contentTypeHeader)?.groups?.get("contentType")?.value?: throw MimeValidationException("Missing content type from ContentType header")
                if (contentType!="multipart/related") throw MimeValidationException("Content type should be multipart/related")
                val boundary = ContentTypeRegex.BOUNDARY.find(contentTypeHeader)?.groups?.get("boundary")?.value?: throw MimeValidationException("Boundary is mandatory on multipart related content")
                ContentTypeRegex.START.find(contentTypeHeader)?.groups?.get("start")?.value?: throw MimeValidationException("Start on multipart request not defined")
                val type = ContentTypeRegex.TYPE.find(contentTypeHeader)?.groups?.get("type")?.value?: throw MimeValidationException("type of multipart / related is undefined")


                if (type != "text/xml") throw MimeValidationException("Type of multipart related should be text/xml")

        }
}


class MimeValidationException(message:String) : Exception(message)