package no.nav.emottak.message.ebxml

object EbXMLConstants {
    const val EBMS_SERVICE_URI = "urn:oasis:names:tc:ebxml-msg:service"
    const val ACKNOWLEDGMENT_ACTION = "Acknowledgment"
    const val MESSAGE_ERROR_ACTION = "MessageError"

    const val OASIS_EBXML_MSG_HEADER_XSD_NS_URI = "http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd"
    const val OASIS_EBXML_MSG_HEADER_TAG = "MessageHeader"

    const val XMLDSIG_NS_URI = "http://www.w3.org/2000/09/xmldsig#"
    const val XMLDSIG_TAG_REFERENCE = "Reference"

    // Disse verdiene skal inkluderes i MessageHeader for utgående meldinger
    // https://git.sarepta.ehelse.no/publisert/standarder/raw/master/kravdokument/OvervaakingMeldingsversjonerEbXML/HIS_1210_2018%20Overv%C3%A5kning%20av%20meldingsversjoner%20i%20ebXML%20-oppdatert.pdf
    const val MSH_SYSTEM = "NAV EBMS"
    val MSH_VERSJON = readVersionFromEnv()

    private fun readVersionFromEnv(): String {
        val imageFullname = System.getenv("NAIS_APP_IMAGE") ?: "myImage:1.0.0"
        return imageFullname.substringAfterLast(":")
    }
}

enum class PartyTypeEnum(val type: String) {
    HER("HER"), ENH("ENH"), ORG("orgnummer")
}
