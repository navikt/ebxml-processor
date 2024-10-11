package no.nav.emottak.cpa

import no.nav.emottak.message.model.Addressing
import no.nav.emottak.message.model.Header
import no.nav.emottak.message.model.Party
import no.nav.emottak.message.model.PartyId
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.util.createCRLFile
import no.nav.emottak.util.createX509Certificate
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement

class TestUtil {
    companion object {
        fun createValidTestCPA(): CollaborationProtocolAgreement {
            val testCpaString = String(this::class.java.classLoader.getResource("cpa/nav-qass-35065.xml").readBytes())
            return unmarshal(testCpaString, CollaborationProtocolAgreement::class.java)
        }

        val validCertificate = createX509Certificate(this::class.java.classLoader.getResource("certificates/valid.qcevident.ca23.ssl.buypass.no.cer").readBytes())
        val expiredCertificate = createX509Certificate(this::class.java.classLoader.getResource("certificates/expired.qcevident.ca23.ssl.buypass.no.cer").readBytes())
        val revokedCertificate = createX509Certificate(this::class.java.classLoader.getResource("certificates/revoked.qcevident.ca23.ssl.buypass.no.cer").readBytes())
        val selfSignedCertificate = createX509Certificate(this::class.java.classLoader.getResource("certificates/cert_selfsigned.pem").readBytes())

        val crlFile = createCRLFile(this::class.java.classLoader.getResource("crl/BPClass3CA2.crl").readBytes())
    }
}

fun createValidValidationRequest() = ValidationRequest(
    messageId = "",
    conversationId = "",
    cpaId = "nav:qass:35065",
    createValidAddressing()
)

fun createValidAddressing() = Addressing(
    to = Party(
        listOf(
            PartyId(
                type = "HER",
                value = "79768"
            )
        ),
        role = "KontrollUtbetaler"
    ),
    from = Party(
        listOf(
            PartyId(
                type = "HER",
                value = "8141253"
            )
        ),
        role = "Behandler"
    ),
    service = "BehandlerKrav",
    action = "OppgjorsMelding"
)

fun createValidTestHeader() = Header(
    messageId = "",
    conversationId = "",
    cpaId = "nav:qass:35065",
    to = Party(
        listOf(
            PartyId(
                type = "HER",
                value = "79768"
            )
        ),
        role = "KontrollUtbetaler"
    ),
    from = Party(
        listOf(
            PartyId(
                type = "HER",
                value = "8141253"
            )
        ),
        role = "Behandler"
    ),
    service = "BehandlerKrav",
    action = "OppgjorsMelding"
)

fun createValidToHERParty() = Party(
    listOf(
        PartyId(
            type = "HER",
            value = "79768"
        )
    ),
    role = "KontrollUtbetaler"
)
fun createValidFromHERParty() = Party(
    listOf(
        PartyId(
            type = "HER",
            value = "8141253"
        )
    ),
    role = "Behandler"
)
