package no.nav.emottak.cpa

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.emottak.cpa.util.EventRegistrationServiceFake
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.decodeBase64
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NhnIntegrationTest {
    val eventRegistrationService = EventRegistrationServiceFake()

    @Test
    fun `Connect to NHN-----`() {
        val expectedCertificate = createX509Certificate(
            decodeBase64("MIIGXTCCBEWgAwIBAgILAaL9PjWyUDg4n1kwDQYJKoZIhvcNAQELBQAwbjELMAkGA1UEBhMCTk8xGDAWBgNVBGEMD05UUk5PLTk4MzE2MzMyNzETMBEGA1UECgwKQnV5cGFzcyBBUzEwMC4GA1UEAwwnQnV5cGFzcyBDbGFzcyAzIFRlc3Q0IENBIEcyIFNUIEJ1c2luZXNzMB4XDTI1MDEwMjA5Mjk0NFoXDTI4MDEwMjIyNTkwMFowgYAxCzAJBgNVBAYTAk5PMRswGQYDVQQKDBJOT1JTSyBIRUxTRU5FVFQgU0YxHTAbBgNVBAsMFFdpbmRvd3NhcHBsaWthc2pvbmVyMRswGQYDVQQDDBJOT1JTSyBIRUxTRU5FVFQgU0YxGDAWBgNVBGEMD05UUk5PLTk5NDU5ODc1OTCCAaIwDQYJKoZIhvcNAQEBBQADggGPADCCAYoCggGBAMGRT1mg2TmQkKUER99Z7KlYkmxYb2cNfYbAaV/jbS1j6tecb3Tm0nstvlSYfoC6S1vq2oGjrR7pIpDah6u/p71IdcP2baFdrzwGu9tFJEwv0Ai0u6Gh4W3+ElraH/ukomf1DB7qR7ngSX+dGVVEYvBiwHRKTauImG+psO6K1sDSG1WerVJvE3RVLaTI5MLXx4jsyl2tbVay86v1bs4VvJBYxnRPx/M6JTpf9i8YBxxmWmyvqURbZWCd21zUJciff3iHWKYpN8iGq/+bOu+3kxPJzAHhT4qkKdrl6TqErA3Pr4TETSh1LFyuKl27K2Zzr/3XaPh6E8ZCT5DykJ6w3An6VSn1VUHk40SzYidEoFF7C/RTs7uLzz9RYuYn2QWUbf/wd0hMuKfnn/XxAjlQ8sJvn8LNI30k5AHAR0DQd3TSWd/e38EdnAsJ14xZXzIJgD5imtZ76GHwus3XnTbTmy/MwbVgsolijtnqGbOW/pNDm7jEJIPJmzF8ajcu0ov7rQIDAQABo4IBZzCCAWMwCQYDVR0TBAIwADAfBgNVHSMEGDAWgBSn/rtsWYitdC5GXnpo+dG7v8+2izAdBgNVHQ4EFgQUNa0xfx7N38clC9z/6J9AWVu0KFkwDgYDVR0PAQH/BAQDAgZAMB8GA1UdIAQYMBYwCgYIYIRCARoBAwIwCAYGBACPegEBMEEGA1UdHwQ6MDgwNqA0oDKGMGh0dHA6Ly9jcmwudGVzdDQuYnV5cGFzc2NhLmNvbS9CUENsM0NhRzJTVEJTLmNybDB7BggrBgEFBQcBAQRvMG0wLQYIKwYBBQUHMAGGIWh0dHA6Ly9vY3NwYnMudGVzdDQuYnV5cGFzc2NhLmNvbTA8BggrBgEFBQcwAoYwaHR0cDovL2NydC50ZXN0NC5idXlwYXNzY2EuY29tL0JQQ2wzQ2FHMlNUQlMuY2VyMCUGCCsGAQUFBwEDBBkwFzAVBggrBgEFBQcLAjAJBgcEAIvsSQECMA0GCSqGSIb3DQEBCwUAA4ICAQBedKchewNDYjjGHxkr2Eka9Wkgnz6/2ZMjPErPOOf9FWnV9kDu3T+OKB6+6bT83p0pwO3h2XuS9G97lMWSfrlaj5/iJzzvBhYzM/IHQKh1LBLtaxdu2dzRGa71yvWG6U+XksKBqyfq/zOJ5cDwxFIsbHOLmAmUtXmJ2x94/WfH/F5uPjAka8C3JeS8BzDLc7LRfwz45hVB8ZBgqHI9szXixjrdM45yfXpOWAiS37TTRWwvqaA9ZXGoBXnrh3SpLva76Ls2LPcXV61iT7emWd4oSBXLsa6uCmbQy8Wmz4w64ulW2Ae8zqMolKgd/oUlJKyZMMMqwybPcO18v4fMktriOBwqZ5dSmIs6M9y5fNuen89D+oCt47PjvIjzOQrdo7r1eAo/LAlHKPB2NrpydHvvsxgTukcWD00wmwK5Cx3u9WJS+QU6M6RX7UaZlE9EIAf5CNLPS5ZpqHY8a4xpOcgKxfss9ecR3sPNsvyqW7/l/7keb2sMFX+cF5nROH021aB9gRiInEiwLZZViEihosdVb9ilxgVk0i5iIMWBwYilUObwNTiE/rQob079Bpmnm0dkVX6r/MMobMCo0QlsL0r6RMJLlAlXmqR5zi6KcA2uGl6QTb5M/O3MkKSMyIsyY6xF4wEsZetIiYMJ6YkVIB8OK8il8Ts80bQYtAt9K7zYDw==".toByteArray())
        )
        val cpa = TestUtil.createValidTestCPA()
        val cert = createX509Certificate(cpa.getPartyInfoByTypeAndID(partyType = "HER", partyId = "8141253").getCertificateForSignatureValidation(role = "Behandler", service = "BehandlerKrav", action = "OppgjorsMelding").certificate)
        assertEquals(expectedCertificate, cert)
    }

    @Test
    fun `Try connecting to NHN`() = nhnTestApp {
        // validateNhn()
    }

    private fun <T> nhnTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        testBlock()
    }
}
