package no.nav.emottak.ebms.model

import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Description
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType

//4.2.3.4.1 https://www.oasis-open.org/committees/ebxml-msg/documents/ebMS_v2_0.pdf#page=31




class EbMSError {
    companion object {
        // ebXml errors
        const val VALUE_NOT_RECOGNIZED = "ValueNotRecognized"
        const val VALUE_NOT_RECOGNIZED_DESCRIPTION = "Element content or attribute value not recognized."

        const val NOT_SUPPORTED = "NotSupported"
        const val NOT_SUPPORTED_DESCRIPTION = "Element content or attribute not supported"

        const val INCONSISTENT = "ValueNotRecognized"
        const val INCONSISTENT_DESCRIPTION =
            "Element content or attribute value inconsistent with other elements or attributes."

        const val OTHER_XML = "OtherXml"
        const val OTHER_XML_DESCRIPTION = "Other error in an element content or attribute value"

        // Non-xml errors
        const val DELIVERY_FAILURE = "DeliveryFailure" // Hvis dette har warning så er det en sjanse for at det gikk bra
        const val DELIVERY_FAILURE_DESCRIPTION = "Message Delivery Failure"

        const val TIME_TO_LIVE_EXPIRED = "TimeToLiveExpired"
        const val TIME_TO_LIVE_EXPIRED_DESCRIPTION = "Message Time To Live Expired"

        const val SECURITY_FAILURE = "SecurityFailure"
        const val SECURITY_FAILURE_DESCRIPTION = "Message Security Checks Failed"

        const val MIME_PROBLEM = "MimeProblem"
        const val MIME_PROBLEM_DESCRIPTION = "URI resolve error"

        const val UNKNOWN = "Unknown"
        const val UNKNOWN_DESCRIPTION = "Unknown Error"
    }

    enum class Code {
        VALUE_NOT_RECOGNIZED,
        NOT_SUPPORTED,
        INCONSISTENT,
        OTHER_XML,
        DELIVERY_FAILURE,
        TIME_TO_LIVE_EXPIRED,
        SECURITY_FAILURE,
        MIME_PROBLEM,
        UNKNOWN,
    }

    fun createError(
        code: Code,
        location: String? = null
    ): Error {
        val error =
            when (code) {
                Code.VALUE_NOT_RECOGNIZED -> createError(
                    errorCode = VALUE_NOT_RECOGNIZED,
                    descriptionText = VALUE_NOT_RECOGNIZED_DESCRIPTION,
                )
                Code.NOT_SUPPORTED -> createError(
                    errorCode = NOT_SUPPORTED,
                    descriptionText = NOT_SUPPORTED_DESCRIPTION,
                )
                Code.INCONSISTENT -> createError(
                    errorCode = INCONSISTENT,
                    descriptionText = INCONSISTENT_DESCRIPTION,
                )
                Code.OTHER_XML -> createError(
                    errorCode = OTHER_XML,
                    descriptionText = OTHER_XML_DESCRIPTION,
                )
                Code.DELIVERY_FAILURE -> createError(
                    errorCode = DELIVERY_FAILURE,
                    descriptionText = DELIVERY_FAILURE_DESCRIPTION,
                )
                Code.TIME_TO_LIVE_EXPIRED -> createError(
                    errorCode = TIME_TO_LIVE_EXPIRED,
                    descriptionText = TIME_TO_LIVE_EXPIRED_DESCRIPTION
                )
                Code.SECURITY_FAILURE -> createError(
                    errorCode = SECURITY_FAILURE,
                    descriptionText = SECURITY_FAILURE_DESCRIPTION
                )
                Code.MIME_PROBLEM -> createError(
                    errorCode = MIME_PROBLEM,
                    descriptionText = MIME_PROBLEM_DESCRIPTION
                )
                Code.UNKNOWN -> createError(
                    errorCode = UNKNOWN,
                    descriptionText = UNKNOWN_DESCRIPTION
                )
            }
        error.location = location
        return error;
    }

    fun createError(
        errorCode: String,
        descriptionText: String? = null,
        severityType: SeverityType? = null,
        location: String? = null
    ): Error {
        val error = Error()
        error.errorCode = errorCode
        if (descriptionText != null) {
            val description = Description()
            description.lang = "no" // Default verdi fra spec.
            description.value = descriptionText
            error.description = description
        }
        error.severity = severityType ?: SeverityType.ERROR
        error.location = location // Content-ID hvis error er i Payload. Hvis ebxml så er det XPath
        error.id = "ERROR_ID" // Element Id
        //error.any             // Unused?
        //error.otherAttributes // Unused?
        //error.codeContext = "urn:oasis:names:tc:ebxml-msg:service:errors" // Skal være default ifølge spec. Trenger ikke overstyre / sette
        return error
    }

    // TODO Send ebxml Error Signal
    fun createErrorList(errors: List<Error>): ErrorList {
        if(errors.isEmpty()) {
            throw IllegalArgumentException("(4.2.3 Kan ikke opprette ErrorList uten errors")
        }
        val errorList = ErrorList()
        errorList.error.addAll(errors)
        errorList.version = "2.0"
        //errorList.any // Unused?
        errorList.id // "May be used for error tracking"
        errorList.highestSeverity = errors.sortedBy { it.severity == SeverityType.ERROR }.first().severity
        errorList.isMustUnderstand = true; // Alltid
        return errorList
    }

}