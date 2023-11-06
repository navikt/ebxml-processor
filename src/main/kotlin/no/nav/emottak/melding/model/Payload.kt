package no.nav.emottak.melding.model

import kotlinx.serialization.Serializable
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Description
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType


@Serializable
data class PayloadRequest(
    val header: Header,
    val payload: ByteArray
)

@Serializable
data class PayloadResponse(
    val processedPayload: ByteArray,
    val error: Error? = null
)

@Serializable
data class Error(val code:ErrorCode,
                 val descriptionText:String,
                 val sevirity:String? = null) {

    fun asEbxmlError(location: String? = null):org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error {
            val error = org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error()
            error.errorCode = this.code.value
            val description = Description()
            description.lang = "no" // Default verdi fra spec.
            description.value = descriptionText
            error.description = description

            error.severity = this.sevirity.takeIf { sevirity!=null}?.let {
                SeverityType.fromValue(it) } ?: SeverityType.ERROR
            error.location = location // Content-ID hvis error er i Payload. Hvis ebxml så er det XPath
            error.id = "ERROR_ID" // Element Id
            //error.any             // Unused?
            //error.otherAttributes // Unused?
            //error.codeContext = "urn:oasis:names:tc:ebxml-msg:service:errors" // Skal være default ifølge spec. Trenger ikke overstyre / sette
            return error
        }

}


@Serializable
data class Header(
    val messageId: String,
    val conversationId: String,
    val cpaId: String,
    val to: Party,
    val from: Party,
    val service: String,
    val action: String
)

@Serializable
data class Party(
    val partyType: String,
    val partyId: String,
    val role: String
)

@Serializable
data class Processing(
    val signingCertificate: SignatureDetails,
    val encryptionCertificate: ByteArray
)

@Serializable
data class ValidationResponse(
    val processing: Processing?,
    val error: List<Error>? = null) {

    fun valid(): Boolean = processing != null
}




 enum class ErrorCode(val value:String,val description:String) {
        VALUE_NOT_RECOGNIZED("ValueNotRecognized","Element content or attribute value not recognized."),
        NOT_SUPPORTED("NotSupported","Element content or attribute not supported"),
        INCONSISTENT("ValueNotRecognized","Element content or attribute value inconsistent with other elements or attributes."),
        OTHER_XML("OtherXml","Other error in an element content or attribute value"),
        DELIVERY_FAILURE("DeliveryFailure","Message Delivery Failure"),
        TIME_TO_LIVE_EXPIRED("TimeToLiveExpired","Message Time To Live Expired"),
        SECURITY_FAILURE("SecurityFailure","Message Security Checks Failed"),
        MIME_PROBLEM("MimeProblem","URI resolve error"),
        UNKNOWN("Unknown","Unknown Error");

     fun createEbxmlError(descriptionText:String, severityType: SeverityType? = null, location: String? = null):org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error {
            val error = org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error()
            error.errorCode = this.value
            val description = Description()
            description.lang = "no" // Default verdi fra spec.
            description.value = descriptionText
            error.description = description

            error.severity = severityType ?: SeverityType.ERROR
            error.location = location // Content-ID hvis error er i Payload. Hvis ebxml så er det XPath
            error.id = "ERROR_ID" // Element Id
            //error.any             // Unused?
            //error.otherAttributes // Unused?
            //error.codeContext = "urn:oasis:names:tc:ebxml-msg:service:errors" // Skal være default ifølge spec. Trenger ikke overstyre / sette
            return error
        }


 }

fun List<org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error>.asErrorList(): ErrorList {
            if(this.isEmpty()) {
                throw IllegalArgumentException("(4.2.3 Kan ikke opprette ErrorList uten errors")
            }
            val errorList = ErrorList()
            errorList.error.addAll(this)
            errorList.version = "2.0"
            //errorList.any // Unused?
            errorList.id // "May be used for error tracking"
            errorList.highestSeverity = this.sortedBy {
                it.severity == SeverityType.ERROR }.first().severity
            errorList.isMustUnderstand = true; // Alltid
            return errorList
}
