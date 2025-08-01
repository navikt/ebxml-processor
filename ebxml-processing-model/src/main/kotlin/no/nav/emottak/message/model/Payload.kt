package no.nav.emottak.message.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import no.nav.emottak.message.util.createUniqueMimeMessageId
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.EndpointTypeType
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Description
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class SendInRequest(
    val messageId: String,
    val conversationId: String,
    val payloadId: String,
    val payload: ByteArray,
    val addressing: Addressing,
    val cpaId: String,
    val ebmsProcessing: EbmsProcessing,
    val signedOf: String? = null,
    val requestId: String,
    val partnerId: Long? = null
)

@Serializable
data class SendInResponse(
    val messageId: String,
    val conversationId: String,
    val addressing: Addressing,
    val payload: ByteArray,
    val requestId: String
)

@Serializable
enum class Direction(val str: String) {
    IN("in"), OUT("out")
}

@Serializable
data class PayloadRequest(
    val direction: Direction,
    val messageId: String,
    val conversationId: String,
    val processing: PayloadProcessing,
    val payload: Payload,
    val addressing: Addressing,
    val requestId: String
)

@Serializable
data class PayloadResponse(
    val processedPayload: Payload? = null,
    val error: Feil? = null,
    val apprec: Boolean = false,
    var juridiskLoggRecordId: String? = null
)

@Serializable
data class Feil(
    val code: ErrorCode,
    val descriptionText: String,
    val severity: String? = null
) {

    fun asEbxmlError(location: String? = null): org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error {
        val error = org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error()
        error.errorCode = this.code.value
        val description = Description()
        description.lang = "no" // Default verdi fra spec.
        description.value = descriptionText
        error.description = description

        error.severity = this.severity.takeIf { severity != null }?.let {
            SeverityType.fromValue(it)
        } ?: SeverityType.ERROR
        error.location = location // Content-ID hvis error er i Payload. Hvis ebxml så er det XPath
        error.id = "ERROR_ID" // Element Id
        // error.any             // Unused?
        // error.otherAttributes // Unused?
        // error.codeContext = "urn:oasis:names:tc:ebxml-msg:service:errors" // Skal være default ifølge spec. Trenger ikke overstyre / sette
        return error
    }
}

@Serializable
data class ValidationRequest(
    val direction: Direction,
    val messageId: String,
    val conversationId: String,
    val cpaId: String,
    val addressing: Addressing
)

@Serializable
data class ValidationResult(
    val ebmsProcessing: EbmsProcessing? = null,
    val payloadProcessing: PayloadProcessing? = null,
    val signalEmailAddress: List<EmailAddress> = emptyList(),
    val receiverEmailAddress: List<EmailAddress> = emptyList(),
    val partnerId: Long? = null,
    val error: List<Feil>? = null
) {
    fun valid(): Boolean = error == null
}

@Serializable
data class PayloadProcessing(
    val signingCertificate: SignatureDetails,
    val encryptionCertificate: ByteArray,
    val processConfig: ProcessConfig
)

@Serializable
data class ProcessConfig(
    val kryptering: Boolean,
    val komprimering: Boolean,
    val signering: Boolean,
    val internformat: Boolean,
    val validering: Boolean,
    val apprec: Boolean, // Kan denne løsrives?
    val ocspSjekk: Boolean,
    val juridiskLogg: Boolean,
    val adapter: String?,
    val errorAction: String?
)

@Serializable
data class EbmsProcessing(
    val test: String = "123",
    val errorAction: String? = null
)

@Serializable
data class Addressing(
    val to: Party,
    val from: Party,
    val service: String,
    val action: String
) {
    fun replyTo(service: String, action: String): Addressing = Addressing(to = from.copy(), from = to.copy(), service, action)
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
    val partyId: List<PartyId>,
    val role: String
)

@Serializable
data class PartyId(
    val type: String,
    val value: String
)

@Serializable
data class EmailAddress(
    val emailAddress: String,
    var type: EndpointTypeType
)

@Serializable
data class Payload(val bytes: ByteArray, val contentType: String, val contentId: String = "att-${createUniqueMimeMessageId()}", val signedBy: String? = null)

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class AsyncPayload(
    @Contextual
    val referenceId: Uuid,
    val contentId: String,
    val contentType: String,
    val content: ByteArray
)

typealias EbmsAttachment = Payload

enum class ErrorCode(val value: String, val description: String) {
    VALUE_NOT_RECOGNIZED("ValueNotRecognized", "Element content or attribute value not recognized."),
    NOT_SUPPORTED("NotSupported", "Element content or attribute not supported"),
    INCONSISTENT("ValueNotRecognized", "Element content or attribute value inconsistent with other elements or attributes."),
    OTHER_XML("OtherXml", "Other error in an element content or attribute value"),
    DELIVERY_FAILURE("DeliveryFailure", "Message Delivery Failure"),
    TIME_TO_LIVE_EXPIRED("TimeToLiveExpired", "Message Time To Live Expired"),
    SECURITY_FAILURE("SecurityFailure", "Message Security Checks Failed"),
    MIME_PROBLEM("MimeProblem", "URI resolve error"),
    UNKNOWN("Unknown", "Unknown Error");

    companion object {
        fun fromString(string: String): ErrorCode {
            return ErrorCode.entries.find {
                it.value.equals(string)
            } ?: throw IllegalArgumentException("unrecognized error code value: $string")
        }
    }
    fun createEbxmlError(
        descriptionText: String? = this.description,
        severityType: SeverityType? = null,
        location: String? = null
    ): org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error {
        val error = org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error()
        error.errorCode = this.value
        val description = Description()
        description.lang = "no" // Default verdi fra spec.
        description.value = descriptionText
        error.description = description
        error.severity = severityType ?: SeverityType.ERROR
        error.location = location // Content-ID hvis error er i Payload. Hvis ebxml så er det XPath
        error.id = "ERROR_ID" // Element Id
        // error.any             // Unused?
        // error.otherAttributes // Unused?
        return error
    }
}

@JvmName("asErrorList")
fun List<Feil>.asErrorList(): ErrorList {
    if (this.isEmpty()) {
        throw IllegalArgumentException("(4.2.3 Kan ikke opprette ErrorList uten errors")
    }

    return this.map {
        it.code.createEbxmlError(it.descriptionText, if (it.severity != null) SeverityType.fromValue(it.severity) else null)
    }.asErrorList()
}

@JvmName("toErrorList")
fun List<org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error>.asErrorList(): ErrorList {
    if (this.isEmpty()) {
        throw IllegalArgumentException("(4.2.3 Kan ikke opprette ErrorList uten errors")
    }
    val errorList = ErrorList()
    errorList.error.addAll(this)
    errorList.version = "2.0"
    // errorList.any // Unused?
    errorList.id // "May be used for error tracking"
    errorList.highestSeverity = this.sortedBy {
        it.severity == SeverityType.ERROR
    }.first().severity
    errorList.isMustUnderstand = true // Alltid
    return errorList
}
