package no.nav.emottak.message.ebxml

object EbXMLConstants {
    const val EBMS_SERVICE_URI = "urn:oasis:names:tc:ebxml-msg:service"
    const val ACKNOWLEDGMENT_ACTION = "Acknowledgment"
    const val MESSAGE_ERROR_ACTION = "MessageError"

    const val OASIS_EBXML_MSG_HEADER_XSD_NS_URI = "http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd"
    const val OASIS_EBXML_MSG_HEADER_TAG = "MessageHeader"

    const val XMLDSIG_NS_URI = "http://www.w3.org/2000/09/xmldsig#"
    const val XMLDSIG_TAG_REFERENCE = "Reference"
}

enum class PartyTypeEnum(val type: String) {
    HER("HER"), ENH("ENH"), ORG("orgnummer")
}
